package tunnel

import (
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/dlclark/regexp2"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

var ErrInvalidType = errors.New("invalid type")

type Provider struct {
	Name        string `json:"name"`
	VehicleType string `json:"vehicleType"`
	Type        string `json:"type"`
	UpdatedAt   int64  `json:"updatedAt"`
}

type UpdatableProvider interface {
	UpdatedAt() time.Time
}

func QueryProviders() []*Provider {
	r := tunnel.RuleProviders()
	p := tunnel.Providers()

	providers := make([]provider.Provider, 0, len(r)+len(p))

	for _, rule := range r {
		if rule.VehicleType() == provider.Compatible {
			continue
		}

		providers = append(providers, rule)
	}

	for _, proxy := range p {
		if proxy.VehicleType() == provider.Compatible {
			continue
		}

		providers = append(providers, proxy)
	}

	result := make([]*Provider, 0, len(providers))

	for _, p := range providers {
		updatedAt := time.Time{}

		if s, ok := p.(UpdatableProvider); ok {
			updatedAt = s.UpdatedAt()
		}

		result = append(result, &Provider{
			Name:        p.Name(),
			VehicleType: p.VehicleType().String(),
			Type:        p.Type().String(),
			UpdatedAt:   updatedAt.UnixNano() / 1000 / 1000,
		})
	}

	return result
}

func QueryProvider(t string, name string, uiSubtitlePattern *regexp2.Regexp) (map[string]any, error) {
	switch t {
	case "Proxy":
		p := tunnel.Providers()[name]
		if p == nil {
			return nil, fmt.Errorf("%s not found", name)
		}

		detail := map[string]any{}
		if payload, err := json.Marshal(p); err == nil {
			_ = json.Unmarshal(payload, &detail)
		}
		updatedAt := time.Time{}
		if s, ok := p.(UpdatableProvider); ok {
			updatedAt = s.UpdatedAt()
		} else if value, ok := detail["updatedAt"].(string); ok {
			if parsed, err := time.Parse(time.RFC3339Nano, value); err == nil {
				updatedAt = parsed
			}
		}

		detail["name"] = p.Name()
		detail["vehicleType"] = p.VehicleType().String()
		detail["type"] = p.Type().String()
		detail["updatedAt"] = updatedAt.UnixNano() / 1000 / 1000
		detail["proxies"] = convertProxies(p.Proxies(), uiSubtitlePattern)
		detail["testUrl"] = p.HealthCheckURL()
		return detail, nil
	default:
		return nil, ErrInvalidType
	}
}

func UpdateProvider(t string, name string) error {
	err := ErrInvalidType

	switch t {
	case "Rule":
		p := tunnel.RuleProviders()[name]
		if p == nil {
			return fmt.Errorf("%s not found", name)
		}

		err = p.Update()
	case "Proxy":
		p := tunnel.Providers()[name]
		if p == nil {
			return fmt.Errorf("%s not found", name)
		}

		err = p.Update()
	}

	if err != nil {
		log.Warnln("Updating provider %s: %s", name, err.Error())
	}

	return err
}
