package zas.admin.zec.backend.actions.authenticate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.RequireAdmin;

import java.util.Optional;

@RestController
@RequestMapping("/api/identity-check")
public class IdentityCheckController {

    private final IdentityCheckService identityCheckService;
    private final UserService userService;

    public IdentityCheckController(IdentityCheckService identityCheckService, UserService userService) {
        this.identityCheckService = identityCheckService;
        this.userService = userService;
    }

    @RequireAdmin
    @PostMapping("/start")
    public ResponseEntity<IdentityCheckResponse> startIDCheck(Authentication auth) {
        var user = userService.getByUsername(auth.getName());
        var idCheckRequest = identityCheckService.createRequestForUser(user);
        var transactionResponse =  identityCheckService.startIdentityCheck(idCheckRequest);
        identityCheckService.saveUserIdentityCheck(auth.getName(), transactionResponse.vipsTransactionId());
        return ResponseEntity.ok(transactionResponse);
    }

    @RequireAdmin
    @GetMapping("/status")
    public ResponseEntity<IdentityCheckStatus> getIDCheckStatus(Authentication auth) {
        var transactionId = identityCheckService.getUserIdentityCheck(auth.getName());
        var transactionStatus = identityCheckService.getIdentityCheckStatus(transactionId);

        return ResponseEntity.ok(Optional.ofNullable(transactionStatus)
                .orElse(new IdentityCheckStatus(null, "NOT_STARTED", null, null, null)));
    }
}
