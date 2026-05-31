package xyz.aimcup.auction.domain.exception;

public class BadRequestException extends DomainException {

    public BadRequestException(String message) {
        super(message);
    }

    @Override
    public String errorType() {
        return "BAD_REQUEST";
    }
}
