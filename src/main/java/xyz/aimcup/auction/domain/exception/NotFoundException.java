package xyz.aimcup.auction.domain.exception;

public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorType() {
        return "NOT_FOUND";
    }
}
