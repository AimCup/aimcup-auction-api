package xyz.aimcup.auction.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.model.OsuUserProfile;
import xyz.aimcup.auction.domain.model.User;
import xyz.aimcup.auction.domain.port.out.UserRepositoryPort;

import java.time.Instant;
import java.util.UUID;

/**
 * Maintains the logged-in user store and decides who is an administrator (allowed to create auctions).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepositoryPort userRepository;
    private final AuctionProperties properties;

    /** Creates or refreshes the user row for an osu! profile after login. */
    public Mono<User> upsert(OsuUserProfile profile) {
        return userRepository.findByOsuId(profile.osuId())
                .defaultIfEmpty(User.builder().id(UUID.randomUUID()).osuId(profile.osuId()).build())
                .flatMap(user -> {
                    if (user.getId() == null) {
                        user.setId(UUID.randomUUID());
                    }
                    user.setOsuId(profile.osuId());
                    user.setUsername(profile.username());
                    user.setAvatarUrl(profile.avatarUrl());
                    user.setCountryCode(profile.countryCode());
                    user.setGlobalRank(profile.globalRank());
                    user.setCountryRank(profile.countryRank());
                    user.setLastLoginAt(Instant.now());
                    return userRepository.save(user);
                });
    }

    public boolean isAdmin(long osuId) {
        AuctionProperties.Security security = properties.getSecurity();
        return security.isDevGrantAdmin()
                || (security.getAdminOsuIds() != null && security.getAdminOsuIds().contains(osuId));
    }

    public Mono<User> findByOsuId(long osuId) {
        return userRepository.findByOsuId(osuId);
    }
}
