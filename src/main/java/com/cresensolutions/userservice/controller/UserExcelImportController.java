package com.cresensolutions.userservice.controller;

import com.cresensolutions.userservice.common.UserConstants;
import com.cresensolutions.userservice.dto.UserImportRow;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.UserRepository;
import com.cresensolutions.userservice.service.UserExcelService;
import com.cresensolutions.userservice.service.UserManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users/import")
public class UserExcelImportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final UserExcelService      excelService;
    private final UserManagementService userManagementService;
    private final UserRepository        userRepository;
    private final ObjectMapper          objectMapper;

    public UserExcelImportController(UserExcelService excelService,
                                     UserManagementService userManagementService,
                                     UserRepository userRepository,
                                     ObjectMapper objectMapper) {
        this.excelService         = excelService;
        this.userManagementService = userManagementService;
        this.userRepository        = userRepository;
        this.objectMapper          = objectMapper;
    }

    // ── GET /template ─────────────────────────────────────────────────────────

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"user_import_template.xlsx\"")
                .contentType(XLSX)
                .body(excelService.generateTemplate());
    }

    // ── POST / ────────────────────────────────────────────────────────────────

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importUsers(
            @RequestParam("file") MultipartFile file,
            @RequestParam("actorUsername") String actorUsername) throws IOException {

        if (file.isEmpty())
            return badRequest("Uploaded file is empty.");

        String name = file.getOriginalFilename();
        if (name == null || (!name.endsWith(".xlsx") && !name.endsWith(".xls")))
            return badRequest("Only .xlsx or .xls files are supported.");

        // ── Build lookup sets in a single pass ──────────────────────────────
        List<UserAccount> allUsers = userRepository.findAllByOrderByUserNameAsc();

        Set<String> existingUsernames = new HashSet<>();
        Set<String> existingEmails    = new HashSet<>();
        allUsers.forEach(u -> {
            existingUsernames.add(u.getUsername().toLowerCase());
            existingEmails.add(u.getEmail().toLowerCase());
        });

        Set<String> managerUsernames = userRepository.findRoleAssignments().stream()
                .filter(r -> UserConstants.ROLE_MANAGER.equalsIgnoreCase(r.getRole()))
                .map(r -> r.getUsername().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());

        // ── Parse + validate ──────────────────────────────────────────────────
        List<UserImportRow> rows = excelService.parseAndValidate(
                file, existingUsernames, existingEmails, managerUsernames);

        if (rows.isEmpty())
            return badRequest("No data rows found in the uploaded file.");

        // ── Partition into valid / invalid via stream ─────────────────────────
        Map<Boolean, List<UserImportRow>> partitioned = rows.stream()
                .collect(Collectors.partitioningBy(r -> r.getErrorReason() == null));

        List<UserImportRow> invalid = partitioned.get(false);
        if (!invalid.isEmpty())
            return errorExcelResponse(rows);

        // ── Import valid rows, collect runtime failures ───────────────────────
        List<UserImportRow> valid = partitioned.get(true);
        AtomicInteger imported = new AtomicInteger();

        valid.forEach(row -> {
            try {
                userManagementService.createUser(excelService.toCreateRequest(row, actorUsername));
                imported.incrementAndGet();
            } catch (Exception e) {
                row.setErrorReason("Import failed: " + e.getMessage());
            }
        });

        boolean anyFailed = valid.stream().anyMatch(r -> r.getErrorReason() != null);
        if (anyFailed)
            return errorExcelResponse(rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message",  imported.get() + " user(s) imported successfully.");
        result.put("imported", imported.get());
        return ResponseEntity.ok(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<?> errorExcelResponse(List<UserImportRow> rows) throws IOException {
        List<UserImportRow> errorRows = rows.stream()
                .filter(r -> r.getErrorReason() != null)
                .toList();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"user_import_errors.xlsx\"");
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Error-Rows");
        headers.setContentType(XLSX);

        try {
            List<Map<String, Object>> summary = errorRows.stream()
                    .map(r -> {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("row",      r.getRowNumber());
                        e.put("username", Objects.toString(r.getUsername(), ""));
                        e.put("reason",   Objects.toString(r.getErrorReason(), ""));
                        return e;
                    })
                    .toList();
            headers.add("X-Error-Rows", objectMapper.writeValueAsString(summary));
        } catch (Exception ignored) {}

        return ResponseEntity.status(422).headers(headers).body(excelService.generateErrorExcel(rows));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }
}
