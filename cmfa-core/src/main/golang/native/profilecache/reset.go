package profilecache

import (
	"sync"
	_ "unsafe"

	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/log"
)

//go:linkname initOnce github.com/metacubex/mihomo/component/profile/cachefile.initOnce
var initOnce sync.Once

//go:linkname defaultCache github.com/metacubex/mihomo/component/profile/cachefile.defaultCache
var defaultCache *cachefile.CacheFile

func Reset() {
	if defaultCache != nil && defaultCache.DB != nil {
		if err := defaultCache.Close(); err != nil {
			log.Warnln("[CacheFile] close cache file failed: %s", err.Error())
		}
	}
	defaultCache = nil
	initOnce = sync.Once{}
}
