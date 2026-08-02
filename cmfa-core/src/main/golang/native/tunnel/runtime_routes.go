package tunnel

import (
	"fmt"
	"net/url"
	"strconv"

	"github.com/dlclark/regexp2"
	"github.com/metacubex/chi"
	"github.com/metacubex/chi/render"
	"github.com/metacubex/http"
	"github.com/metacubex/mihomo/common/utils"
	controller "github.com/metacubex/mihomo/hub/route"
)

var runtimeSubtitlePatternProvider = func() *regexp2.Regexp { return nil }

func ApplyRuntimeSubtitlePatternProvider(provider func() *regexp2.Regexp) {
	if provider == nil {
		runtimeSubtitlePatternProvider = func() *regexp2.Regexp { return nil }
		return
	}
	runtimeSubtitlePatternProvider = provider
}

type runtimeRouteOperations struct {
	snapshot  func() (*RuntimeProxySnapshot, error)
	nodeDelay func(
		id RuntimeProxyID,
		url string,
		expected string,
		timeoutMillis int,
	) (*DelayResponse, error)
	groupDelay func(
		name string,
		url string,
		expected string,
		timeoutMillis int,
	) (*DelayResponse, error)
	providerDelay func(
		name string,
		url string,
		expected string,
		timeoutMillis int,
	) (*DelayResponse, error)
}

type runtimeDelayRouteParameters struct {
	url           string
	expected      string
	timeoutMillis int
}

func init() {
	controller.Register(func(router chi.Router) {
		router.Mount("/asterisk/runtime", newRuntimeRouter(defaultRuntimeRouteOperations()))
	})
}

func defaultRuntimeRouteOperations() runtimeRouteOperations {
	return runtimeRouteOperations{
		snapshot: func() (*RuntimeProxySnapshot, error) {
			return QueryRuntimeProxySnapshot(runtimeSubtitlePatternProvider())
		},
		nodeDelay:     QueryNodeDelay,
		groupDelay:    QueryRuntimeGroupDelay,
		providerDelay: QueryRuntimeProviderDelay,
	}
}

func newRuntimeRouter(operations runtimeRouteOperations) http.Handler {
	router := chi.NewRouter()
	router.Get("/proxies", func(w http.ResponseWriter, request *http.Request) {
		if operations.snapshot == nil {
			runtimeRouteError(w, request, http.StatusInternalServerError, fmt.Errorf("runtime proxy snapshot operation is unavailable"))
			return
		}
		response, err := operations.snapshot()
		if err != nil {
			runtimeRouteError(w, request, http.StatusInternalServerError, err)
			return
		}
		render.JSON(w, request, response)
	})
	router.Get("/delay/node", func(w http.ResponseWriter, request *http.Request) {
		parameters, err := runtimeRouteDelayParameters(request)
		if err != nil {
			runtimeRouteError(w, request, http.StatusBadRequest, err)
			return
		}
		if operations.nodeDelay == nil {
			runtimeRouteError(w, request, http.StatusInternalServerError, fmt.Errorf("node delay operation is unavailable"))
			return
		}
		query := request.URL.Query()
		name := query.Get("name")
		if name == "" {
			runtimeRouteError(w, request, http.StatusBadRequest, fmt.Errorf("proxy name is required"))
			return
		}
		response, err := operations.nodeDelay(
			RuntimeProxyID{
				Name:         name,
				ProviderName: query.Get("provider"),
			},
			parameters.url,
			parameters.expected,
			parameters.timeoutMillis,
		)
		if err != nil {
			runtimeRouteError(w, request, http.StatusNotFound, err)
			return
		}
		render.JSON(w, request, response)
	})
	router.Get("/delay/group/{name}", runtimeBatchDelayHandler(operations.groupDelay, "group"))
	router.Get("/delay/provider/{name}", runtimeBatchDelayHandler(operations.providerDelay, "provider"))
	return router
}

func runtimeBatchDelayHandler(
	operation func(string, string, string, int) (*DelayResponse, error),
	kind string,
) http.HandlerFunc {
	return func(w http.ResponseWriter, request *http.Request) {
		parameters, err := runtimeRouteDelayParameters(request)
		if err != nil {
			runtimeRouteError(w, request, http.StatusBadRequest, err)
			return
		}
		if operation == nil {
			runtimeRouteError(w, request, http.StatusInternalServerError, fmt.Errorf("%s delay operation is unavailable", kind))
			return
		}
		response, err := operation(
			runtimeRoutePathParam(request, "name"),
			parameters.url,
			parameters.expected,
			parameters.timeoutMillis,
		)
		if err != nil {
			runtimeRouteError(w, request, http.StatusNotFound, err)
			return
		}
		render.JSON(w, request, response)
	}
}

func runtimeRoutePathParam(request *http.Request, name string) string {
	value := chi.URLParam(request, name)
	if unescaped, err := url.PathUnescape(value); err == nil {
		return unescaped
	}
	return value
}

func runtimeRouteTimeout(request *http.Request) (int, error) {
	raw := request.URL.Query().Get("timeout")
	timeoutMillis, err := strconv.Atoi(raw)
	if err != nil || timeoutMillis <= 0 {
		return 0, fmt.Errorf("timeout must be a positive integer")
	}
	return timeoutMillis, nil
}

func runtimeRouteDelayParameters(request *http.Request) (runtimeDelayRouteParameters, error) {
	timeoutMillis, err := runtimeRouteTimeout(request)
	if err != nil {
		return runtimeDelayRouteParameters{}, err
	}
	query := request.URL.Query()
	expected := query.Get("expected")
	if _, err := utils.NewUnsignedRanges[uint16](expected); err != nil {
		return runtimeDelayRouteParameters{}, fmt.Errorf("expected status is invalid: %w", err)
	}
	return runtimeDelayRouteParameters{
		url:           query.Get("url"),
		expected:      expected,
		timeoutMillis: timeoutMillis,
	}, nil
}

func runtimeRouteError(
	w http.ResponseWriter,
	request *http.Request,
	status int,
	err error,
) {
	render.Status(request, status)
	render.JSON(w, request, render.M{"error": err.Error()})
}
