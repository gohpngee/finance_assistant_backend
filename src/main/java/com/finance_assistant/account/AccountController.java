package com.finance_assistant.account;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        AccountResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/accounts/" + response.id())).body(response);
    }

    @GetMapping
    public List<AccountResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable long id) {
        return service.get(id);
    }
}
