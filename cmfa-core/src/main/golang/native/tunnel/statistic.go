package tunnel

import (
	"github.com/metacubex/mihomo/tunnel/statistic"
)

func ResetStatistic() {
	statistic.DefaultManager.ResetStatistic()
}

func Now() (up int64, down int64) {
	return statistic.DefaultManager.Now()
}

func Total() (up int64, down int64) {
	return statistic.DefaultManager.Total()
}

func Memory() uint64 {
	return statistic.DefaultManager.Memory()
}

func ConnectionCount() int {
	count := 0
	statistic.DefaultManager.Range(func(_ statistic.Tracker) bool {
		count++
		return true
	})
	return count
}

func Snapshot() *statistic.Snapshot {
	return statistic.DefaultManager.Snapshot()
}

func CloseConnection(id string) bool {
	connection := statistic.DefaultManager.Get(id)
	if connection == nil {
		return false
	}
	_ = connection.Close()
	return true
}
