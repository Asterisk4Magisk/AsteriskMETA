package config

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	U "net/url"
	"os"
	P "path"
	"runtime"
	"strconv"
	"strings"
	"time"

	"cfa/native/app"

	A "github.com/metacubex/mihomo/adapter/outbound"
	"github.com/metacubex/mihomo/adapter/provider"
	"github.com/metacubex/mihomo/component/dialer"
	clashHttp "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/component/proxydialer"
	C "github.com/metacubex/mihomo/constant"
	RB "github.com/metacubex/mihomo/rules/bundle"
)

type FetchProxy struct {
	Host     string `json:"host"`
	Port     int    `json:"port"`
	Username string `json:"username"`
	Password string `json:"password"`
}

type FetchOptions struct {
	Force     bool        `json:"force"`
	UserAgent string      `json:"userAgent"`
	Proxy     *FetchProxy `json:"proxy"`
}

type Status struct {
	Action            string   `json:"action"`
	Args              []string `json:"args"`
	Progress          int      `json:"progress"`
	MaxProgress       int      `json:"max"`
	SubUpload         *int64   `json:"subUpload,omitempty"`
	SubDownload       *int64   `json:"subDownload,omitempty"`
	SubTotal          *int64   `json:"subTotal,omitempty"`
	SubExpire         *int64   `json:"subExpire,omitempty"`
	SubUpdateInterval *int64   `json:"subUpdateInterval,omitempty"`
}

type fetchHeader struct {
	SubscriptionUserInfo  string
	ProfileUpdateInterval string
}

func openUrl(ctx context.Context, url string, userAgent string, requestDialer C.Dialer) (io.ReadCloser, fetchHeader, error) {
	response, err := clashHttp.HttpRequest(
		ctx,
		url,
		http.MethodGet,
		http.Header{"User-Agent": {userAgent}},
		nil,
		clashHttp.WithDialer(requestDialer),
	)

	if err != nil {
		return nil, fetchHeader{}, err
	}
	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		_ = response.Body.Close()
		return nil, fetchHeader{}, fmt.Errorf("HTTP %d", response.StatusCode)
	}

	return response.Body, fetchHeader{
		SubscriptionUserInfo:  response.Header.Get("subscription-userinfo"),
		ProfileUpdateInterval: response.Header.Get("profile-update-interval"),
	}, nil
}

func openContent(url string) (io.ReadCloser, error) {
	return app.OpenContent(url)
}

func fetch(ctx context.Context, url *U.URL, file string, userAgent string, requestDialer C.Dialer) (fetchHeader, error) {
	requestCtx, cancel := context.WithTimeout(ctx, 60*time.Second)
	defer cancel()

	var reader io.ReadCloser
	var header fetchHeader
	var err error

	switch url.Scheme {
	case "http", "https":
		reader, header, err = openUrl(requestCtx, url.String(), userAgent, requestDialer)
	case "content":
		reader, err = openContent(url.String())
	default:
		err = fmt.Errorf("unsupported scheme %s of %s", url.Scheme, url)
	}

	if err != nil {
		return fetchHeader{}, err
	}

	defer reader.Close()

	return header, writeFile(file, reader)
}

func writeFile(file string, reader io.Reader) error {
	_ = os.MkdirAll(P.Dir(file), 0700)

	f, err := os.OpenFile(file, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0600)
	if err != nil {
		return err
	}

	defer f.Close()

	_, err = io.Copy(f, reader)
	if err != nil {
		_ = os.Remove(file)
	}

	return err
}

func parseProfileUpdateInterval(value string) (int64, bool) {
	hours, err := strconv.ParseInt(strings.TrimSpace(value), 10, 64)
	if err != nil {
		return 0, false
	}

	if hours <= 0 {
		return 0, true
	}

	interval := time.Duration(hours) * time.Hour
	if interval < 15*time.Minute {
		interval = 15 * time.Minute
	}

	return int64(interval / time.Millisecond), true
}

func reportSubscriptionInfo(header fetchHeader, reportStatus func(string)) {
	userinfo := header.SubscriptionUserInfo
	updateIntervalHeader := header.ProfileUpdateInterval
	if userinfo == "" && updateIntervalHeader == "" {
		return
	}

	status := Status{
		Action:      "SubscriptionInfo",
		Args:        []string{},
		Progress:    -1,
		MaxProgress: -1,
	}

	if userinfo != "" {
		info := provider.NewSubscriptionInfo(userinfo)
		expire := info.Expire * 1000
		status.SubUpload = &info.Upload
		status.SubDownload = &info.Download
		status.SubTotal = &info.Total
		status.SubExpire = &expire
	}

	if interval, ok := parseProfileUpdateInterval(updateIntervalHeader); ok {
		status.SubUpdateInterval = &interval
	}

	bytes, _ := json.Marshal(&status)
	reportStatus(string(bytes))
}

func FetchAndValid(
	ctx context.Context,
	path string,
	url string,
	options FetchOptions,
	reportStatus func(string),
) error {
	requestDialer, err := options.requestDialer()
	if err != nil {
		return err
	}
	defaultUserAgent := "ClashMetaForAndroid/" + app.VersionName()
	mainUserAgent := strings.TrimSpace(options.UserAgent)
	if mainUserAgent == "" {
		mainUserAgent = defaultUserAgent
	}
	configPath := P.Join(path, "config.yaml")

	if _, err := os.Stat(configPath); os.IsNotExist(err) || options.Force {
		url, err := U.Parse(url)
		if err != nil {
			return err
		}

		bytes, _ := json.Marshal(&Status{
			Action:      "FetchConfiguration",
			Args:        []string{url.Host},
			Progress:    -1,
			MaxProgress: -1,
		})

		reportStatus(string(bytes))

		header, err := fetch(ctx, url, configPath, mainUserAgent, requestDialer)
		if err != nil {
			return err
		}

		reportSubscriptionInfo(header, reportStatus)
	}

	defer runtime.GC()

	statusBytes, _ := json.Marshal(&Status{
		Action:      "Decrypting",
		Args:        []string{},
		Progress:    -1,
		MaxProgress: -1,
	})
	reportStatus(string(statusBytes))

	configData, err := os.ReadFile(configPath)
	if err != nil {
		return err
	}
	configData, err = DecryptBytes(configData)
	if err != nil {
		return err
	}

	statusBytes, _ = json.Marshal(&Status{
		Action:      "Verifying",
		Args:        []string{},
		Progress:    -1,
		MaxProgress: -1,
	})
	reportStatus(string(statusBytes))

	rawCfg, err := UnmarshalAndPatchBytes(path, configData)
	if err != nil {
		return err
	}

	providerErrors := make([]error, 0)
	forEachProviders(rawCfg, func(index int, total int, name string, provider map[string]any, prefix string) {
		if err := ctx.Err(); err != nil {
			providerErrors = append(providerErrors, err)
			return
		}
		bytes, _ := json.Marshal(&Status{
			Action:      "FetchProviders",
			Args:        []string{name},
			Progress:    index,
			MaxProgress: total,
		})

		reportStatus(string(bytes))

		u, uok := provider["url"]
		p, pok := provider["path"]

		if !uok || !pok {
			return
		}

		us, uok := u.(string)
		ps, pok := p.(string)

		if !uok || !pok {
			return
		}

		if _, err := os.Stat(ps); err == nil {
			return
		}

		if prefix == RULES {
			if pib, uok := provider["path-in-bundle"]; uok {
				if pib, uok := pib.(string); uok && pib != "" {
					// The core extracts files from the bundle, but CMFA fetches provider
					// content during profile loading, so keep that observable behavior.
					if file, err := RB.Open(pib); err == nil {
						defer file.Close()
						if err := writeFile(ps, file); err == nil {
							return
						}
					}
				}
			}
		}

		url, err := U.Parse(us)
		if err != nil {
			providerErrors = append(providerErrors, fmt.Errorf("parse provider %s URL: %w", name, err))
			return
		}

		if _, err := fetch(ctx, url, ps, defaultUserAgent, requestDialer); err != nil {
			providerErrors = append(providerErrors, fmt.Errorf("fetch provider %s: %w", name, err))
		}
	})
	if err := errors.Join(providerErrors...); err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}

	statusBytes, _ = json.Marshal(&Status{
		Action:      "Verifying",
		Args:        []string{},
		Progress:    0xffff,
		MaxProgress: 0xffff,
	})

	reportStatus(string(statusBytes))

	cfg, err := Parse(rawCfg)
	if err != nil {
		return err
	}

	destroyProviders(cfg)

	return nil
}

func (options FetchOptions) requestDialer() (C.Dialer, error) {
	if options.Proxy == nil {
		return dialer.NewDialer(), nil
	}
	proxy := options.Proxy
	if strings.TrimSpace(proxy.Host) == "" || proxy.Port <= 0 || proxy.Port > 65535 {
		return nil, fmt.Errorf("invalid fetch proxy address")
	}
	adapter, err := A.NewSocks5(A.Socks5Option{
		Name:     "AsteriskMETA subscription fetch",
		Server:   proxy.Host,
		Port:     proxy.Port,
		UserName: proxy.Username,
		Password: proxy.Password,
	})
	if err != nil {
		return nil, err
	}
	return proxydialer.New(adapter, false), nil
}
