package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * POS transaction endpoint consumed by the Angular cashier/transaction view.
 * <p>
 * POST /api/v1/transactions creates a Transaction entity via the generic entity store.
 * The receipt number is generated server-side so it's unique and auditable.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final GenericEntityService genericEntityService;
    private final ObjectMapper objectMapper;

    private static final String TRANSACTION_TYPE = "Transaction";

    /**
     * POST /api/v1/transactions
     * Records a POS sale transaction.
     * <p>
     * Request body: { "amount": 12.50, "paymentMethod": "CASH", "notes": "..." }
     * Response: { "receiptNumber": "R-...", "amount": 12.50, "paymentMethod": "CASH", "timestamp": "..." }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CASHIER', 'BARISTA', 'SHIFT_SUPERVISOR', 'STORE_MANAGER')")
    public ResponseEntity<Object> createTransaction(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        log.info("POST /transactions - amount={}, method={}, cashier={}",
                body.path("amount").asDouble(), body.path("paymentMethod").asString(), caller.getUsername());

        String receiptNumber = "R-" + System.currentTimeMillis();
        String timestamp = Instant.now().toString();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("receiptNumber", receiptNumber);
        payload.put("amount",        body.path("amount").asDouble());
        payload.put("paymentMethod", body.path("paymentMethod").asString("CASH"));
        payload.put("notes",         body.path("notes").asString(""));
        payload.put("cashierId",     caller.getUserId().toString());
        payload.put("timestamp",     timestamp);

        EntityData saved = genericEntityService.create(TRANSACTION_TYPE, payload, caller);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("receiptNumber",  receiptNumber);
        response.put("amount",         saved.getPayload().path("amount").asDouble());
        response.put("paymentMethod",  saved.getPayload().path("paymentMethod").asString());
        response.put("timestamp",      timestamp);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
