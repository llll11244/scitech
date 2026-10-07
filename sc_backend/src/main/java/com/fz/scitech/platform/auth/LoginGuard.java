package com.fz.scitech.platform.auth;

import com.fz.scitech.common.BizException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定(决策#35-⑤):同账号连续错 5 次锁 10 分钟。
 * ConcurrentHashMap 内存计数,单机部署足够;重启即清零(可接受)。
 */
@Component
public class LoginGuard {

    private static final int MAX_FAILS = 5;
    private static final long LOCK_MILLIS = 10 * 60_000L;

    /** 每账号失败计数与锁定截止时刻 */
    private static class Attempt {
        int fails;
        long lockUntil;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 登录前检查:锁定中直接拒绝 */
    public void checkLocked(String username) {
        Attempt a = attempts.get(username);
        if (a == null) {
            return;
        }
        if (a.lockUntil > System.currentTimeMillis()) {
            long remainMin = (a.lockUntil - System.currentTimeMillis()) / 60_000 + 1;
            throw new BizException("失败次数过多,账号已锁定,请约 " + remainMin + " 分钟后再试");
        }
    }

    /** 密码错误后计数;达到 5 次上锁并清零计数(锁期满后重新累计) */
    public void recordFail(String username) {
        Attempt a = attempts.computeIfAbsent(username, k -> new Attempt());
        synchronized (a) {
            a.fails++;
            if (a.fails >= MAX_FAILS) {
                a.lockUntil = System.currentTimeMillis() + LOCK_MILLIS;
                a.fails = 0;
            }
        }
    }

    /** 登录成功清计数 */
    public void reset(String username) {
        attempts.remove(username);
    }
}
