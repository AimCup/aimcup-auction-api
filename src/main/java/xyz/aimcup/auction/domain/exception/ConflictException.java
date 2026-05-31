package xyz.aimcup.auction.domain.exception;

public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public String errorType() {
        return "CONFLICT";
    }
}
