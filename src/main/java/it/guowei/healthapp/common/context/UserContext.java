package it.guowei.healthapp.common.context;

/**
 * 用户上下文（ThreadLocal）
 * 存储当前登录用户信息，避免方法间频繁传参
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    public static void set(UserInfo userInfo) {
        HOLDER.set(userInfo);
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        UserInfo info = HOLDER.get();
        return info != null ? info.getUserId() : null;
    }

    public static String getUsername() {
        UserInfo info = HOLDER.get();
        return info != null ? info.getUsername() : null;
    }

    public static Integer getUserType() {
        UserInfo info = HOLDER.get();
        return info != null ? info.getUserType() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static class UserInfo {
        private Long userId;
        private String username;
        private Integer userType; // 1-患者 2-医生 3-管理员
        private String token;

        public UserInfo() {}

        public UserInfo(Long userId, String username, Integer userType) {
            this.userId = userId;
            this.username = username;
            this.userType = userType;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Integer getUserType() { return userType; }
        public void setUserType(Integer userType) { this.userType = userType; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}
