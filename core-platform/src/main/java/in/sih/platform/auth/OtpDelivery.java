package in.sih.platform.auth;

public interface OtpDelivery {
    void send(String phone, String otp);
}
