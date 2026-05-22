package com.mygrinlog.auth;

import java.lang.annotation.*;

/** 컨트롤러 파라미터에 붙여서 현재 로그인 유저 ID 를 자동 주입받는다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
