# telegram-bot-client

The simplest Telegram Bot API client for Java.

Features:
* Minimal dependencies — just [Gson](https://github.com/google/gson) and SLF4J
* Small footprint
* Plain `HttpURLConnection` under the hood — no HTTP client to pull in
* Immutable request objects built with Lombok builders

## How to add it into your app

### Maven

```xml
<repositories>
    <repository>
        <id>pne</id>
        <name>payneteasy repo</name>
        <url>https://maven.pne.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.payneteasy</groupId>
    <artifactId>telegram-bot-client</artifactId>
    <version>1.0-9</version>
</dependency>
```

## Quick start

Create a service by wrapping the HTTP client with your bot token, then call the API:

```java
import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.impl.TelegramServiceImpl;
import com.payneteasy.telegram.bot.client.http.TelegramHttpClientImpl;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;

String botToken = System.getenv("BOT_TOKEN");
ITelegramService telegram = new TelegramServiceImpl(new TelegramHttpClientImpl(botToken));

telegram.sendMessage(TelegramMessageRequest.builder()
        .chatId(123456789L)
        .text("Hello from telegram-bot-client!")
        .build());
```

`TelegramHttpClientImpl(String token)` uses sensible defaults (30s connect/read/write timeouts and
`https://api.telegram.org/bot`). Use the full constructor
`TelegramHttpClientImpl(baseUrl, token, HttpClientTimeouts, Gson)` if you need to customize them.

## Sending messages

Requests are created with builders. For formatted text, pass a `ParseMode`
(`MarkdownV2`, `HTML` or legacy `Markdown`):

```java
TelegramMessage sent = telegram.sendMessage(TelegramMessageRequest.builder()
        .chatId(123456789L)
        .text("<b>Bold</b> and <i>italic</i>")
        .parseMode(ParseMode.HTML)
        .build());

long messageId = sent.getResult().getMessageId();
```

Edit a message you already sent:

```java
telegram.editMessageText(EditMessageTextRequest.builder()
        .chatId(123456789L)
        .messageId(messageId)
        .text("Updated text")
        .build());
```

## Receiving updates (long polling)

```java
int offset = 0;
while (true) {
    TelegramGetUpdatesResponse response = telegram.getUpdates(TelegramGetUpdatesRequest.builder()
            .offset(offset)
            .timeout(30)
            .build());

    for (Update update : response.getResult()) {
        offset = update.getUpdateId().intValue() + 1; // acknowledge processed updates
        if (update.getMessage() != null) {
            System.out.println("Got: " + update.getMessage().getText());
        }
    }
}
```

Alternatively, register a webhook instead of polling:

```java
telegram.setWebhook(new TelegramWebhookRequest("https://example.com/telegram/webhook"));
// ...
telegram.clearWebhook();
```

## Other common methods

```java
// "typing…" chat action
telegram.sendChatAction(ChatActionRequest.builder()
        .chatId(123456789L)
        .action(ChatActionRequest.Action.typing)
        .build());

// bot command menu
telegram.setMyCommands(TelegramSetMyCommandsRequest.builder()
        .commands(Arrays.asList(
                new BotCommand("start", "Start the bot"),
                new BotCommand("help",  "Show help")))
        .build());

// bot description shown in an empty chat
telegram.setMyDescription(SetMyDescriptionRequest.builder()
        .description("A demo bot")
        .build());
```

## Payments

Send an invoice. Amounts are integers in the smallest units of the currency
(e.g. `1500` = `15.00 USD`); the `prices` list is shown in order:

```java
telegram.sendInvoice(TelegramInvoiceRequest.builder()
        .chatId(123456789L)
        .title("Premium subscription")
        .description("One month of premium access")
        .payload("order-42")                       // opaque, echoed back to you
        .paymentProviderToken("PROVIDER_TOKEN")
        .currency("USD")
        .labeledPrices(Arrays.asList(
                LabeledPrice.builder().label("Subscription").amount(1500).build(),
                LabeledPrice.builder().label("Tax").amount(120).build()))
        .needShippingAddress(true)                  // set when you need a shipping step
        .isFlexible(true)
        .build());
```

Handle the checkout flow as updates arrive:

```java
for (Update update : response.getResult()) {

    // 1. flexible invoices trigger a shipping query — reply with shipping options
    if (update.getShippingQuery() != null) {
        ShippingQuery query = update.getShippingQuery();
        telegram.answerShippingQuery(TelegramAnswerShippingQueryRequest.builder()
                .shippingQueryId(query.getId())
                .ok(true)
                .shippingOptions(Arrays.asList(
                        ShippingOption.builder()
                                .id("express")
                                .title("Express delivery")
                                .prices(Arrays.asList(
                                        LabeledPrice.builder().label("Delivery").amount(500).build()))
                                .build()))
                .build());
    }

    // 2. just before payment Telegram sends a pre-checkout query — you must answer within 10s
    if (update.getPreCheckoutQuery() != null) {
        PreCheckoutQuery query = update.getPreCheckoutQuery();
        telegram.answerPreCheckoutQuery(TelegramAnswerPreCheckoutQueryRequest.builder()
                .preCheckoutQueryId(query.getId())
                .ok(true)                            // or .ok(false).errorMessage("Out of stock")
                .build());
    }

    // 3. a successful payment arrives as a normal message
    if (update.getMessage() != null && update.getMessage().getSuccessfulPayment() != null) {
        SuccessfulPayment payment = update.getMessage().getSuccessfulPayment();
        System.out.println("Paid " + payment.getTotalAmount() + " " + payment.getCurrency()
                + " for " + payment.getInvoicePayload());
    }
}
```

## Error handling

Any non-successful API call throws `TelegramCommandException`, which exposes the HTTP error code and,
on `429 Too Many Requests`, the `retry_after` hint from Telegram:

```java
try {
    telegram.sendMessage(request);
} catch (TelegramCommandException e) {
    if (Integer.valueOf(429).equals(e.getErrorCode()) && e.getRetryAfter() != null) {
        Thread.sleep(e.getRetryAfter() * 1000L); // back off, then retry
    } else {
        throw e;
    }
}
```

## Build & test

```bash
mvn clean install   # build the jar
mvn test            # run the unit tests
```

Requires JDK 8+ and Maven 3.
