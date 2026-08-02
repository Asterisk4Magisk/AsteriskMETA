package tunnel

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/dlclark/regexp2"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/tunnel"
)

const (
	DelayStatusSuccess = "success"
	DelayStatusTimeout = "timeout"
	DelayStatusFailed  = "failed"
)

type RuntimeProxyID struct {
	Name         string `json:"name"`
	ProviderName string `json:"providerName,omitempty"`
}

type DelayMeasurement struct {
	ID     RuntimeProxyID `json:"id"`
	Delay  int            `json:"delay,omitempty"`
	Status string         `json:"status"`
	Error  string         `json:"error,omitempty"`
}

type DelayResponse struct {
	Measurements []DelayMeasurement `json:"measurements"`
}

type RuntimeProxy struct {
	ID       RuntimeProxyID `json:"id"`
	Title    string         `json:"title"`
	Subtitle string         `json:"subtitle"`
	Type     string         `json:"type"`
	Delay    int            `json:"delay"`
	IsGroup  bool           `json:"isGroup"`
}

type RuntimeProxyGroup struct {
	Name    string          `json:"name"`
	Type    string          `json:"type"`
	Now     string          `json:"now"`
	Proxies []*RuntimeProxy `json:"proxies"`
	Hidden  bool            `json:"hidden"`
	Icon    string          `json:"icon"`
	TestURL string          `json:"testUrl"`
}

type RuntimeProxySnapshot struct {
	Groups []*RuntimeProxyGroup `json:"groups"`
}

type delayTestFunc func(
	ctx context.Context,
	url string,
	expectedStatus utils.IntRanges[uint16],
) (uint16, error)

type delayTarget struct {
	id              RuntimeProxyID
	test            delayTestFunc
	aliveForTestURL func(string) bool
}

var errUnexpectedResponseStatus = errors.New("unexpected response status")

func runtimeProxyID(proxy C.Proxy) RuntimeProxyID {
	return RuntimeProxyID{
		Name:         proxy.Name(),
		ProviderName: proxy.ProxyInfo().ProviderName,
	}
}

func resolveRuntimeProxy(id RuntimeProxyID) (C.Proxy, error) {
	return resolveRuntimeProxyFrom(id, tunnel.Proxies(), tunnel.Providers())
}

func resolveRuntimeProxyFrom(
	id RuntimeProxyID,
	proxies map[string]C.Proxy,
	providers map[string]P.ProxyProvider,
) (C.Proxy, error) {
	if id.Name == "" {
		return nil, fmt.Errorf("proxy name is empty")
	}
	if id.ProviderName == "" {
		proxy := proxies[id.Name]
		if proxy == nil {
			return nil, fmt.Errorf("proxy `%s` not found", id.Name)
		}
		return proxy, nil
	}

	provider := providers[id.ProviderName]
	if provider == nil {
		return nil, fmt.Errorf("proxy provider `%s` not found", id.ProviderName)
	}
	for _, proxy := range provider.Proxies() {
		if proxy.Name() == id.Name {
			return proxy, nil
		}
	}
	return nil, fmt.Errorf("proxy `%s` not found in provider `%s`", id.Name, id.ProviderName)
}

func QueryRuntimeProxySnapshot(uiSubtitlePattern *regexp2.Regexp) (*RuntimeProxySnapshot, error) {
	groupNames := QueryProxyGroupNames(false)
	groups := make([]*RuntimeProxyGroup, 0, len(groupNames))
	for _, name := range groupNames {
		group, err := runtimeProxyGroup(tunnel.Proxies()[name], uiSubtitlePattern)
		if err != nil {
			return nil, fmt.Errorf("query group `%s`: %w", name, err)
		}
		groups = append(groups, group)
	}
	return &RuntimeProxySnapshot{Groups: groups}, nil
}

func runtimeProxyGroup(proxy C.Proxy, uiSubtitlePattern *regexp2.Regexp) (*RuntimeProxyGroup, error) {
	if proxy == nil {
		return nil, fmt.Errorf("group not found")
	}
	group, ok := proxy.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		return nil, fmt.Errorf("proxy `%s` has invalid type %s", proxy.Name(), proxy.Type().String())
	}
	testURL := runtimeGroupTestURL(proxy)
	proxies := group.Proxies()
	members := make([]*RuntimeProxy, 0, len(proxies))
	for _, member := range proxies {
		members = append(members, runtimeProxy(member, testURL, uiSubtitlePattern))
	}
	return &RuntimeProxyGroup{
		Name:    proxy.Name(),
		Type:    proxy.Type().String(),
		Now:     group.Now(),
		Proxies: members,
		Hidden:  group.Hidden(),
		Icon:    group.Icon(),
		TestURL: testURL,
	}, nil
}

func runtimeProxy(proxy C.Proxy, testURL string, uiSubtitlePattern *regexp2.Regexp) *RuntimeProxy {
	name := proxy.Name()
	title := name
	subtitle := proxy.Type().String()
	if uiSubtitlePattern != nil {
		if _, isGroup := proxy.Adapter().(outboundgroup.ProxyGroup); !isGroup {
			runes := []rune(name)
			match, err := uiSubtitlePattern.FindRunesMatch(runes)
			if err == nil && match != nil {
				title = string(runes[:match.Index]) + string(runes[match.Index+match.Length:])
				subtitle = string(runes[match.Index : match.Index+match.Length])
			}
		}
	}
	if testURL == "" {
		testURL = C.DefaultTestURL
	}
	_, isGroup := proxy.Adapter().(outboundgroup.ProxyGroup)
	return &RuntimeProxy{
		ID:       runtimeProxyID(proxy),
		Title:    strings.TrimSpace(title),
		Subtitle: strings.TrimSpace(subtitle),
		Type:     proxy.Type().String(),
		Delay:    int(proxy.LastDelayForTestUrl(testURL)),
		IsGroup:  isGroup,
	}
}

func runtimeGroupTestURL(proxy C.Proxy) string {
	payload, err := json.Marshal(proxy)
	if err != nil {
		return C.DefaultTestURL
	}
	var detail struct {
		TestURL string `json:"testUrl"`
	}
	if err := json.Unmarshal(payload, &detail); err != nil || detail.TestURL == "" {
		return C.DefaultTestURL
	}
	return detail.TestURL
}

func delayTargetForProxy(proxy C.Proxy) delayTarget {
	return delayTarget{
		id:              runtimeProxyID(proxy),
		aliveForTestURL: proxy.AliveForTestUrl,
		test: func(
			ctx context.Context,
			url string,
			expectedStatus utils.IntRanges[uint16],
		) (uint16, error) {
			return proxy.URLTest(ctx, url, expectedStatus)
		},
	}
}

func delayTargetsForProviderFrom(
	name string,
	providers map[string]P.ProxyProvider,
) ([]delayTarget, string, error) {
	provider := providers[name]
	if provider == nil {
		return nil, "", fmt.Errorf("proxy provider `%s` not found", name)
	}
	proxies := provider.Proxies()
	targets := make([]delayTarget, 0, len(proxies))
	for _, proxy := range proxies {
		targets = append(targets, delayTargetForProxy(proxy))
	}
	return targets, provider.HealthCheckURL(), nil
}

func QueryNodeDelay(
	id RuntimeProxyID,
	url string,
	expected string,
	timeoutMillis int,
) (*DelayResponse, error) {
	proxy, err := resolveRuntimeProxy(id)
	if err != nil {
		return nil, err
	}
	return queryDelayTargets([]delayTarget{delayTargetForProxy(proxy)}, url, expected, timeoutMillis)
}

func QueryRuntimeGroupDelay(
	name string,
	url string,
	expected string,
	timeoutMillis int,
) (*DelayResponse, error) {
	proxy := tunnel.Proxies()[name]
	if proxy == nil {
		return nil, fmt.Errorf("group `%s` not found", name)
	}
	group, ok := proxy.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		return nil, fmt.Errorf("group `%s` has invalid type %s", name, proxy.Type().String())
	}
	if url == "" {
		url = runtimeGroupTestURL(proxy)
	}
	proxies := group.Proxies()
	targets := make([]delayTarget, 0, len(proxies))
	for _, member := range proxies {
		targets = append(targets, delayTargetForProxy(member))
	}
	return queryDelayTargets(targets, url, expected, timeoutMillis)
}

func QueryRuntimeProviderDelay(
	name string,
	url string,
	expected string,
	timeoutMillis int,
) (*DelayResponse, error) {
	targets, providerURL, err := delayTargetsForProviderFrom(name, tunnel.Providers())
	if err != nil {
		return nil, err
	}
	if url == "" {
		url = providerURL
	}
	return queryDelayTargets(targets, url, expected, timeoutMillis)
}

func queryDelayTargets(
	targets []delayTarget,
	url string,
	expected string,
	timeoutMillis int,
) (*DelayResponse, error) {
	if timeoutMillis <= 0 {
		return nil, fmt.Errorf("timeout must be positive")
	}
	if url == "" {
		url = C.DefaultTestURL
	}
	expectedStatus, err := utils.NewUnsignedRanges[uint16](expected)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Millisecond*time.Duration(timeoutMillis))
	defer cancel()
	return &DelayResponse{
		Measurements: runDelayTargets(ctx, targets, url, expectedStatus),
	}, nil
}

func runDelayTargets(
	ctx context.Context,
	targets []delayTarget,
	url string,
	expectedStatus utils.IntRanges[uint16],
) []DelayMeasurement {
	results := make([]DelayMeasurement, len(targets))
	var wg sync.WaitGroup
	for index, target := range targets {
		index, target := index, target
		wg.Add(1)
		go func() {
			defer wg.Done()
			delay, err := target.test(ctx, url, expectedStatus)
			if err == nil && target.aliveForTestURL != nil && !target.aliveForTestURL(url) {
				err = errUnexpectedResponseStatus
			}
			results[index] = delayMeasurement(target.id, delay, err)
		}()
	}
	wg.Wait()
	return results
}

func delayMeasurement(id RuntimeProxyID, delay uint16, err error) DelayMeasurement {
	measurement := DelayMeasurement{ID: id}
	switch {
	case err == nil && delay > 0:
		measurement.Status = DelayStatusSuccess
		measurement.Delay = int(delay)
	case errors.Is(err, context.DeadlineExceeded) || errors.Is(err, context.Canceled):
		measurement.Status = DelayStatusTimeout
	case err != nil:
		measurement.Status = DelayStatusFailed
		measurement.Error = err.Error()
	default:
		measurement.Status = DelayStatusFailed
		measurement.Error = "delay test returned zero"
	}
	return measurement
}
