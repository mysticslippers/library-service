package me.ifmo.backend.notification.application.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.ifmo.backend.notification.application.NotificationChannelSender;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class SmsRuNotificationSender implements NotificationChannelSender {

    private record SmsRuSendResponse(
            String status,
            @JsonProperty("status_code") Integer statusCode,
            @JsonProperty("status_text") String statusText,
            Map<String, SmsRuMessageResponse> sms
    ) {}

    private record SmsRuMessageResponse(
            String status,
            @JsonProperty("status_code") Integer statusCode,
            @JsonProperty("status_text") String statusText,
            @JsonProperty("sms_id") String smsId
    ) {}

    private final RestClient restClient;
    private final SmsRuProperties properties;

    @Autowired
    public SmsRuNotificationSender(RestClient.Builder restClientBuilder, SmsRuProperties properties) {
        this(buildRestClient(restClientBuilder, properties), properties);
    }

    SmsRuNotificationSender(RestClient restClient, SmsRuProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient buildRestClient(RestClient.Builder builder, SmsRuProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.clone().requestFactory(requestFactory).build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public NotificationDeliveryResult send(Notification notification) {
        if (notification.getChannel() != NotificationChannel.SMS)
            return failure("Notification channel '%s' is not supported by SMS.RU".formatted(notification.getChannel()));

        if (properties.getApiId() == null || properties.getApiId().isBlank())
            return failure("SMS.RU API id is not configured");

        if (notification.getUser() == null)
            return failure("SMS notification recipient must not be null");

        String phone = normalizePhone(notification.getUser().getPhone());
        if (phone == null)
            return failure("SMS notification recipient phone must match '+7XXXXXXXXXX' or '8XXXXXXXXXX'");

        if (notification.getBody() == null || notification.getBody().isBlank())
            return failure("SMS notification body must not be blank");

        MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("api_id", properties.getApiId());
        request.add("to", phone);
        request.add("msg", notification.getBody());
        request.add("json", "1");

        if (properties.getSender() != null && !properties.getSender().isBlank())
            request.add("from", properties.getSender());
        if (properties.isTestMode())
            request.add("test", "1");

        try {
            SmsRuSendResponse response = restClient.post()
                    .uri(properties.getApiUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(request)
                    .retrieve()
                    .body(SmsRuSendResponse.class);

            return toDeliveryResult(phone, response);
        } catch (RestClientException exception) {
            String errorMessage = exception.getMessage();
            return failure(errorMessage != null && !errorMessage.isBlank()
                    ? errorMessage
                    : exception.getClass().getSimpleName());
        }
    }

    private NotificationDeliveryResult toDeliveryResult(String phone, SmsRuSendResponse response) {
        if (response == null)
            return failure("SMS.RU returned an empty response");

        if (!"OK".equalsIgnoreCase(response.status()))
            return failure(providerError("SMS.RU request failed", response.statusCode(), response.statusText()));

        SmsRuMessageResponse message = response.sms() != null ? response.sms().get(phone) : null;
        if (message == null && response.sms() != null && response.sms().size() == 1)
            message = response.sms().values().iterator().next();

        if (message == null)
            return failure("SMS.RU response does not contain the requested phone");

        if (!"OK".equalsIgnoreCase(message.status()) || message.smsId() == null || message.smsId().isBlank())
            return failure(providerError("SMS.RU rejected the message", message.statusCode(), message.statusText()));

        return new NotificationDeliveryResult(true, message.smsId(), null);
    }

    private String providerError(String prefix, Integer statusCode, String statusText) {
        String code = statusCode != null ? " (code %d)".formatted(statusCode) : "";
        String details = statusText != null && !statusText.isBlank() ? ": " + statusText : "";
        return prefix + code + details;
    }

    private String normalizePhone(String phone) {
        if (phone == null)
            return null;

        String normalized = phone.strip();
        if (normalized.matches("\\+7[0-9]{10}"))
            return normalized.substring(1);
        if (normalized.matches("8[0-9]{10}"))
            return "7" + normalized.substring(1);
        return null;
    }

    private NotificationDeliveryResult failure(String errorMessage) {
        return new NotificationDeliveryResult(false, null, errorMessage);
    }
}
