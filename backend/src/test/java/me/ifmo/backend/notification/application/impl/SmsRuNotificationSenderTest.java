package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SmsRuNotificationSenderTest {

    private static final String API_URL = "https://sms.ru/sms/send";

    private MockRestServiceServer server;
    private SmsRuProperties properties;
    private SmsRuNotificationSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        properties = new SmsRuProperties();
        properties.setApiUrl(URI.create(API_URL));
        properties.setApiId("test-api-id");
        properties.setTestMode(true);
        sender = new SmsRuNotificationSender(builder.build(), properties);
    }

    @Test
    void sendsSmsInTestModeAndReturnsProviderMessageId() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(allOf(
                        containsString("api_id=test-api-id"),
                        containsString("to=79991234567"),
                        containsString("msg=Your+reservation+is+ready."),
                        containsString("json=1"),
                        containsString("test=1")
                )))
                .andRespond(withSuccess("""
                        {
                          "status": "OK",
                          "status_code": 100,
                          "sms": {
                            "79991234567": {
                              "status": "OK",
                              "status_code": 100,
                              "sms_id": "000000-10000000"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = sender.send(notification("+79991234567"));

        assertThat(result.success()).isTrue();
        assertThat(result.externalMessageId()).isEqualTo("000000-10000000");
        assertThat(result.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void normalizesPhoneStartingWithEight() {
        server.expect(requestTo(API_URL))
                .andExpect(content().string(containsString("to=79991234567")))
                .andRespond(withSuccess("""
                        {
                          "status": "OK",
                          "sms": {
                            "79991234567": {
                              "status": "OK",
                              "status_code": 100,
                              "sms_id": "sms-id"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = sender.send(notification("89991234567"));

        assertThat(result.success()).isTrue();
        server.verify();
    }

    @Test
    void returnsProviderMessageError() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {
                          "status": "OK",
                          "status_code": 100,
                          "sms": {
                            "79991234567": {
                              "status": "ERROR",
                              "status_code": 207,
                              "status_text": "Recipient is unavailable"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = sender.send(notification("+79991234567"));

        assertThat(result.success()).isFalse();
        assertThat(result.externalMessageId()).isNull();
        assertThat(result.errorMessage()).contains("207").contains("Recipient is unavailable");
        server.verify();
    }

    @Test
    void returnsFailureForHttpError() {
        server.expect(requestTo(API_URL)).andRespond(withServerError());

        var result = sender.send(notification("+79991234567"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotBlank();
        server.verify();
    }

    @Test
    void rejectsInvalidPhoneWithoutCallingProvider() {
        var result = sender.send(notification("12345"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("phone");
        server.verify();
    }

    @Test
    void rejectsMissingApiIdWithoutCallingProvider() {
        properties.setApiId(" ");

        var result = sender.send(notification("+79991234567"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("API id");
        server.verify();
    }

    private Notification notification(String phone) {
        return Notification.builder()
                .user(User.builder().id(1L).phone(phone).build())
                .channel(NotificationChannel.SMS)
                .body("Your reservation is ready.")
                .build();
    }
}
