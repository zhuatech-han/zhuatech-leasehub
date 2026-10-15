// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** HTTP会话、CSRF与密码失效测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:leasehubhttp;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "leasehub.admin-password=TEST-Only-Password123!"
    })
@AutoConfigureMockMvc
@Transactional
class HttpSecurityTest {
  @Autowired MockMvc mvc;
  String login = "{\"username\":\"admin\",\"password\":\"TEST-Only-Password123!\"}";

  MockHttpSession login() throws Exception {
    return (MockHttpSession)
        mvc.perform(
                post("/api/auth/login").with(csrf()).contentType("application/json").content(login))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession();
  }

  @Test
  void anonymousAndCsrfGuard() throws Exception {
    mvc.perform(get("/api/data/assets")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").contentType("application/json").content(login))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void loginProfileDoesNotExposeHashAndLogoutInvalidates() throws Exception {
    var s = login();
    String profile =
        mvc.perform(get("/api/auth/me").session(s))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(profile.contains("password_hash"));
    mvc.perform(post("/api/auth/logout").session(s).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void passwordChangeRevokesOldSession() throws Exception {
    var s = login();
    mvc.perform(
            post("/api/auth/password")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"oldPassword\":\"TEST-Only-Password123!\",\"newPassword\":\"TEST-New-Password123!\"}"))
        .andExpect(status().isOk());
    assertTrue(s.isInvalid());
    mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(login))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownLoginIsRateLimitedWithoutAccountExistenceDisclosure() throws Exception {
    for (int i = 0; i < 8; i++)
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      "{\"username\":\"test_unknown\",\"password\":\"TEST-Only-Password123!\"}"))
          .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"username\":\"test_unknown\",\"password\":\"TEST-Only-Password123!\"}"))
        .andExpect(status().isTooManyRequests());
  }
}
