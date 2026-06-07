package tunnel

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

func HealthCheck(name string) {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)

		return
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Request health check for `%s`: invalid type %s", name, p.Type().String())

		return
	}

	wg := &sync.WaitGroup{}

	for _, pr := range g.Providers() {
		wg.Add(1)

		go func(provider provider.ProxyProvider) {
			provider.HealthCheck()

			wg.Done()
		}(pr)
	}

	wg.Wait()
}

func HealthCheckAll() {
	for _, g := range QueryProxyGroupNames(false) {
		go func(group string) {
			HealthCheck(group)
		}(g)
	}
}

func QueryProxyDelay(name string, url string, timeoutMillis int) (uint16, error) {
	p := tunnel.Proxies()[name]
	if p == nil {
		return 0, fmt.Errorf("proxy `%s` not found", name)
	}

	expectedStatus, err := utils.NewUnsignedRanges[uint16]("")
	if err != nil {
		return 0, err
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Millisecond*time.Duration(timeoutMillis))
	defer cancel()

	delay, err := p.URLTest(ctx, url, expectedStatus)
	if ctx.Err() != nil {
		return 0, ctx.Err()
	}
	if err != nil {
		return delay, err
	}
	if delay == 0 {
		return 0, fmt.Errorf("proxy `%s` delay test returned zero", name)
	}

	return delay, nil
}

func QueryGroupDelay(name string, url string, timeoutMillis int) (map[string]int, error) {
	p := tunnel.Proxies()[name]
	if p == nil {
		return nil, fmt.Errorf("group `%s` not found", name)
	}

	group, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		return nil, fmt.Errorf("group `%s` has invalid type %s", name, p.Type().String())
	}

	if selectAble, ok := p.Adapter().(outboundgroup.SelectAble); ok && p.Type() != C.Selector {
		selectAble.ForceSet("")
		cachefile.Cache().SetSelected(p.Name(), "")
	}

	expectedStatus, err := utils.NewUnsignedRanges[uint16]("")
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Millisecond*time.Duration(timeoutMillis))
	defer cancel()

	delays, err := group.URLTest(ctx, url, expectedStatus)
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}
	if err != nil {
		return nil, err
	}

	result := make(map[string]int, len(delays))
	for proxyName, delay := range delays {
		result[proxyName] = int(delay)
	}

	return result, nil
}
