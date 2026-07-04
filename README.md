# aimcup-auction-api

Reactive backend for **live player auctions** in aimcup tournaments. Captains bid on players in
real time from the web or from Discord; won players are assigned to the captain's team.

Built with **Spring WebFlux + GraphQL**, a **reactive MongoDB** store, **Redis** for the live
snapshot, **Discord4J** for the bot, and **osu! OAuth2 + JWT** for auth. Organised with a
**hexagonal architecture**.

---

## Architecture (hexagonal / ports & adapters)

```
xyz.aimcup.auction
├─ domain
│  ├─ model          pure domain (Auction, Player, Captain, Manager, BidEvent, LiveAuctionState …)
│  ├─ port.in        inbound use-case interfaces (admin, control, bidding, query, channel-lookup)
│  └─ port.out       outbound ports (AuctionRepository, UserRepository, OsuApi, LiveState, DiscordNotification)
├─ application
│  ├─ engine         AuctionEngine — the reactive auction state machine (timers, bids, stages)
│  └─ service        AuctionAdminService, AuctionQueryService, UserService
├─ adapters
│  ├─ in.graphql     GraphQL query/mutation/subscription resolvers + DTOs + mapper
│  ├─ in.rest        osu! OAuth2 controller
│  └─ out            persistence (Mongo), cache (Redis), osu (WebClient), discord (Discord4J)
├─ security          JWT, GraphQL auth interceptor (HTTP + WS), presence tracker
└─ config            properties, security, redis
```

The domain has **no framework annotations**; persistence uses `ReactiveMongoTemplate` (the `id`
field is treated as `_id` by convention), keeping the boundary clean.

### The auction engine

`AuctionEngine` is the single source of truth for running auctions. Every state change — a web/Discord
bid, an organizer command, or a timer firing — is funnelled onto **one worker thread**, so mutations
are serialised without locks. Slow work (Mongo / Redis / Discord) is dispatched off that thread.

Instead of ticking every second, the engine schedules **one deadline per phase** and broadcasts a
snapshot carrying `phaseEndsAtEpochMs`; clients run the countdown locally. A new bid cancels and
reschedules that deadline. The live snapshot is mirrored to **Redis** on every change; the durable
**MongoDB** store is written when a player's bidding completes (and at start/pause/resume/finish).

---

## Prerequisites

- Java 21 (Maven wrapper included — no Maven install needed)
- Docker (for MongoDB + Redis)

## Run it

```bash
# 1. Start MongoDB (:11002) and Redis (:6380)
docker compose -f docker-compose.dev.yml up -d

# 2. Provide local secrets (first time only — this file is gitignored)
cp src/main/resources/application-dev.yml.dist src/main/resources/application-dev.yml
#    then edit application-dev.yml and fill in your osu!/Discord secrets and a JWT secret

# 3. Start the API on :8080 (defaults to the `dev` profile)
./mvnw spring-boot:run            # or: SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

- GraphQL endpoint: `POST http://localhost:8080/graphql`
- GraphQL WebSocket (subscriptions): `ws://localhost:8080/graphql`
- GraphiQL playground: `http://localhost:8080/graphiql`
- osu! login: `GET http://localhost:8080/oauth2/authorize/osu?redirect=/`

### Configuration

All settings live under `auction.*` in `src/main/resources/application.yml`. Key knobs:

| Property | Default | Meaning |
| --- | --- | --- |
| `auction.security.dev-grant-admin` | `true` | Local dev: every signed-in osu! user is `ROLE_ADMIN` (can create auctions). Set `false` + use `auction.security.admin-osu-ids` in production. |
| `auction.discord.enabled` | `true` | Connects the Discord bot. Degrades to a no-op if the token is invalid. |
| `auction.jwt.secret` | (from env / `application-dev.yml`) | HS256 signing secret. |

**Secrets are never committed.** `application.yml` carries no real credential values: the osu!
OAuth/API secrets, the Discord bot token and the JWT signing key come from the **gitignored
`application-dev.yml`** for local runs (template: `application-dev.yml.dist`) and from **environment
variables** in deployment (`OSU_CLIENT_SECRET`, `OSU_API_CLIENT_SECRET`, `DISCORD_BOT_TOKEN`,
`AUCTION_JWT_SECRET`). The app fails fast on startup if the JWT secret is missing.

## Authentication

1. The SPA sends the browser to `/oauth2/authorize/osu?redirect=<path>`.
2. We redirect to osu!, handle the callback, fetch `/me`, upsert the user, and mint a **JWT**.
3. The browser lands on `<frontend>/auth/callback?token=…&redirect=…`.

The JWT is sent as `Authorization: Bearer …` on HTTP GraphQL requests and in the `connection_init`
payload for the subscription WebSocket. A `GraphQlAuthInterceptor` verifies it for both transports.
Viewing auctions / live state is public; **only captains can bid** and **only managers can manage**
— enforced in the resolvers, services and engine.

## Discord bot

When `auction.discord.enabled=true` the bot connects and registers global slash commands:

| Command | Who | Action |
| --- | --- | --- |
| `/bid amount:<n>` | captains | Bid on the current player |
| `/maxbid` | captains | Instantly win with the configured max bid |
| `/ready` | captains | Confirm readiness before the auction starts (and after a pause) |
| `/pause`, `/resume` | organizers | Pause / resume the auction |
| `/removeplayer osu_id:<n>` | organizers | Remove a sold player from their team & refund |
| `/setbalance captain_osu_id:<n> amount:<n>` | organizers | Override a captain's balance |

Set the auction's **guild id** and **channel id** (Settings tab) to mirror the live auction into a
channel. Global commands can take up to an hour to appear in a new server.

## GraphQL at a glance

```graphql
query   me, auction(id), myAuctions, recentAuctions(limit), liveState(auctionId)
mutation createAuction, updateAuctionSettings, updateAuctionStages, updateAuctionMeta, deleteAuction,
         addManager, removeManager, addPlayer, importPlayers(csv), removePlayer, setCaptain, unsetCaptain,
         startAuction, pauseAuction, resumeAuction, removePlayerFromTeam, changeCaptainBalance,
         placeBid(amount), placeMaxBid
subscription liveAuction(auctionId)   # the whole public page renders from this
```

CSV import expects columns `username, osuId, description, qualificationRank, bestBeatmapUrl,
bestBeatmapAccuracy, worstBeatmapUrl, worstBeatmapAccuracy` (header optional). `username`, `osuId`
and `qualificationRank` are required; the description, beatmap URLs and accuracies may be empty.
Fields with commas (e.g. a description) must be wrapped in double quotes. See
`docs/sample-players.csv`.
