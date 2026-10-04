package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.security.UserPrincipal;
import com.neelastack.lakhdatar.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceService finance;

    private UserPrincipal p(Authentication a) { return (UserPrincipal) a.getPrincipal(); }

    @GetMapping("/overview")
    FinanceService.Overview overview(Authentication a) { return finance.overview(p(a)); }

    @GetMapping("/ledger/cursor")
    FinanceService.CursorPage<FinanceService.LedgerRow> ledgerCursor(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String entryType,@RequestParam(required=false) @Size(max=500) String cursor,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int size,Authentication a){ return finance.ledgerCursor(p(a),q,entryType,cursor,size); }

    @GetMapping("/refunds/cursor")
    FinanceService.CursorPage<FinanceService.RefundRow> refundsCursor(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String status,@RequestParam(required=false) @Size(max=500) String cursor,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int size,Authentication a){ return finance.refundsCursor(p(a),q,status,cursor,size); }

    @GetMapping("/ledger")
    FinanceService.PageView<FinanceService.LedgerRow> ledger(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String entryType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            Authentication a) {
        return finance.ledger(p(a), q, entryType, page, size);
    }

    @GetMapping("/refunds")
    FinanceService.PageView<FinanceService.RefundRow> refunds(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            Authentication a) {
        return finance.refunds(p(a), q, status, page, size);
    }
}
