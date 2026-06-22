package com.cresensolutions.userservice.common;

public final class UserConstants {

    private UserConstants() {}
    
    public static final String ROLE_ADMIN    = "ADMIN";
    public static final String ROLE_MANAGER  = "MANAGER";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";

    public static final String COMPANY_ID_PREFIX      = "CRESEN";
    public static final int    COMPANY_ID_NUMBER_WIDTH = 3;
    public static final int    MINIMUM_NEXT_COMPANY_ID = 4;

    public static final String TMPL_PASSWORD_RESET = "PASSWORD_RESET_OTP";
    public static final String TMPL_USER_CREATED   = "USER_CREATED";
    public static final String TMPL_USER_DELETED   = "USER_DELETED";
    public static final String TMPL_ROLE_CHANGED   = "USER_ROLE_CHANGED";

    public static final String LOGO_CID = "cresenSolutionsLogo";
}
