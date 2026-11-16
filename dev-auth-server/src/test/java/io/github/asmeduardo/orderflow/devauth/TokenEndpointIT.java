package io.github.asmeduardo.orderflow.devauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(
        properties = {
            "ORDERFLOW_READER_CLIENT_SECRET=reader-test-secret",
            "ORDERFLOW_OPERATOR_CLIENT_SECRET=operator-test-secret"
        })
@AutoConfigureMockMvc
class TokenEndpointIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void operatorReceivesAJwtForTheOrderflowApiWithBothScopes() throws Exception {
        var response = token("orderflow-operator", "operator-test-secret", "orderflow.read orderflow.operate");

        assertThat(response).hasStatusOk();
        var jwt = jwtDecoder.decode(JsonPath.read(response.getResponse().getContentAsString(), "$.access_token"));
        assertThat(jwt.getSubject()).isEqualTo("orderflow-operator");
        assertThat(jwt.getAudience()).containsExactly("orderflow-api");
        assertThat(jwt.getIssuer()).hasToString("http://localhost:9000");
        assertThat(jwt.getClaimAsStringList("scope")).containsExactlyInAnyOrder("orderflow.read", "orderflow.operate");
    }

    @Test
    void readerCannotObtainTheOperateScope() {
        assertThat(token("orderflow-reader", "reader-test-secret", "orderflow.operate"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("invalid_scope");
    }

    @Test
    void clientMaySendItsSecretInTheRequestBody() {
        var response = mvc.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formField("grant_type", "client_credentials")
                .formField("scope", "orderflow.read")
                .formField("client_id", "orderflow-reader")
                .formField("client_secret", "reader-test-secret")
                .exchange();

        assertThat(response).hasStatusOk().bodyJson().hasPath("$.access_token");
    }

    @Test
    void wrongSecretIsRejected() {
        assertThat(token("orderflow-reader", "not-the-secret", "orderflow.read"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult token(String clientId, String secret, String scope) {
        return mvc.post()
                .uri("/oauth2/token")
                .with(httpBasic(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formField("grant_type", "client_credentials")
                .formField("scope", scope)
                .exchange();
    }
}
