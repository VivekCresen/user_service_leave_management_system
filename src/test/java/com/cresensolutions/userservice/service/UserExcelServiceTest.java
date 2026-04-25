package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.UserImportRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserExcelServiceTest {

    private UserExcelService service;

    @BeforeEach
    void setUp() {
        service = new UserExcelService();
    }


    @Test
    void generateTemplate_returnsNonEmptyBytes() throws IOException {
        byte[] bytes = service.generateTemplate();
        assertThat(bytes).isNotEmpty();
    }

    @Test
    void generateTemplate_producesValidXlsx() throws IOException {
        byte[] bytes = service.generateTemplate();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(1);
            assertThat(headerRow).isNotNull();
            assertThat(headerRow.getCell(0).getStringCellValue()).contains("Full Name");
        }
    }

    @Test
    void generateTemplate_hasInstructionsSheet() throws IOException {
        byte[] bytes = service.generateTemplate();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(2);
            assertThat(wb.getSheetName(1)).isEqualTo("Instructions");
        }
    }

    @Test
    void parseAndValidate_validRow_returnsNoError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "manager1", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(
                file,
                Set.of(),
                Set.of(),
                Set.of("manager1")
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getErrorReason()).isNull();
    }

    @Test
    void parseAndValidate_blankFullName_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Full Name is required");
    }

    @Test
    void parseAndValidate_fullNameTooLong_setsError() throws IOException {
        String longName = "A".repeat(256);
        MockMultipartFile file = buildExcelFile(new String[][]{
            { longName, "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Full Name must not exceed 255");
    }

    @Test
    void parseAndValidate_blankUsername_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Username is required");
    }

    @Test
    void parseAndValidate_invalidUsernamePattern_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "jo", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Username must be 3-100 chars");
    }

    @Test
    void parseAndValidate_duplicateUsernameInFile_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" },
            { "Jane Doe", "john.doe", "jane@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Female", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).isNull();
        assertThat(rows.get(1).getErrorReason()).contains("already exists or is duplicated");
    }

    @Test
    void parseAndValidate_existingUsername_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "existing", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of("existing"), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("already exists");
    }

    @Test
    void parseAndValidate_blankEmail_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Email is required");
    }

    @Test
    void parseAndValidate_invalidEmailFormat_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "not-an-email", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Invalid email format");
    }

    @Test
    void parseAndValidate_emailTooLong_setsError() throws IOException {
        String longEmail = "a".repeat(195) + "@b.com";
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", longEmail, "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Email must not exceed 200");
    }

    @Test
    void parseAndValidate_duplicateEmailInFile_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "same@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" },
            { "Jane Doe", "jane.doe", "same@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Female", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).isNull();
        assertThat(rows.get(1).getErrorReason()).contains("already exists or is duplicated");
    }

    @Test
    void parseAndValidate_existingEmail_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "existing@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of("existing@cresensolutions.com"), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("already exists");
    }

    @Test
    void parseAndValidate_blankPassword_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Password is required");
    }

    @Test
    void parseAndValidate_tooShortPassword_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Ab1!", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Password must be at least");
    }

    @Test
    void parseAndValidate_weakPassword_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "weakpassword", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("uppercase, lowercase, digit, and special character");
    }

    @Test
    void parseAndValidate_blankRole_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Role is required");
    }

    @Test
    void parseAndValidate_invalidRole_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "ADMIN", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Role must be MANAGER or EMPLOYEE");
    }

    @Test
    void parseAndValidate_managerRoleIsValid() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "MANAGER", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).isNull();
    }

    @Test
    void parseAndValidate_unknownManagerUsername_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "ghost_mgr", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of("real_mgr"));

        assertThat(rows.get(0).getErrorReason()).contains("not found");
    }

    @Test
    void parseAndValidate_blankManagerUsername_isAllowed() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).isNull();
    }

    @Test
    void parseAndValidate_blankGender_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Gender is required");
    }

    @Test
    void parseAndValidate_invalidGender_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Unknown", "true" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Gender must be");
    }

    @Test
    void parseAndValidate_allValidGenders_noError() throws IOException {
        String[][] rows = {
            { "A", "user.a", "a@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "true" },
            { "B", "user.b", "b@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Female", "true" },
            { "C", "user.c", "c@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Other", "true" },
            { "D", "user.d", "d@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Prefer not to say", "true" },
        };
        MockMultipartFile file = buildExcelFile(rows);

        List<UserImportRow> result = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        result.forEach(r -> assertThat(r.getErrorReason()).isNull());
    }

    @Test
    void parseAndValidate_blankActive_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Active is required");
    }

    @Test
    void parseAndValidate_invalidActive_setsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "John Doe", "john.doe", "john@cresensolutions.com", "Pass@1234", "EMPLOYEE", "", "Male", "yes" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows.get(0).getErrorReason()).contains("Active must be 'true' or 'false'");
    }

    @Test
    void parseAndValidate_emptySheet_returnsEmptyList() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[0][]);

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows).isEmpty();
    }

    @Test
    void parseAndValidate_skipsNotesRow() throws IOException {
        MockMultipartFile file = buildExcelFileWithNotesRow();

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        assertThat(rows).isEmpty();
    }

    @Test
    void parseAndValidate_multipleErrors_concatenatedWithSemicolon() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
            { "", "", "bad-email", "", "ADMIN", "", "", "" }
        });

        List<UserImportRow> rows = service.parseAndValidate(file, Set.of(), Set.of(), Set.of());

        String error = rows.get(0).getErrorReason();
        assertThat(error).contains(";");
    }

    @Test
    void generateErrorExcel_withErrorRows_returnsNonEmptyBytes() throws IOException {
        UserImportRow errorRow = new UserImportRow();
        errorRow.setRowNumber(3);
        errorRow.setFullName("Bad User");
        errorRow.setUsername("bad.user");
        errorRow.setEmail("bad@cresensolutions.com");
        errorRow.setPassword("Pass@1234");
        errorRow.setRole("EMPLOYEE");
        errorRow.setManagerUsername("");
        errorRow.setGender("Male");
        errorRow.setActive("true");
        errorRow.setErrorReason("Username already exists");

        byte[] bytes = service.generateErrorExcel(List.of(errorRow));

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void generateErrorExcel_withNoErrorRows_returnsEmptySheet() throws IOException {
        UserImportRow validRow = new UserImportRow();
        validRow.setRowNumber(3);
        validRow.setFullName("Good User");
        validRow.setUsername("good.user");
        validRow.setEmail("good@cresensolutions.com");
        validRow.setPassword("Pass@1234");
        validRow.setRole("EMPLOYEE");
        validRow.setManagerUsername("");
        validRow.setGender("Male");
        validRow.setActive("true");
    
        byte[] bytes = service.generateErrorExcel(List.of(validRow));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(0);
        }
    }

    @Test
    void generateErrorExcel_hasErrorSummarySheet() throws IOException {
        UserImportRow errorRow = new UserImportRow();
        errorRow.setRowNumber(3);
        errorRow.setFullName("Bad");
        errorRow.setUsername("bad.user");
        errorRow.setEmail("bad@cresensolutions.com");
        errorRow.setPassword("Pass@1234");
        errorRow.setRole("EMPLOYEE");
        errorRow.setManagerUsername("");
        errorRow.setGender("Male");
        errorRow.setActive("true");
        errorRow.setErrorReason("Some error");

        byte[] bytes = service.generateErrorExcel(List.of(errorRow));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void generateErrorExcel_nullFieldsInRow_handledGracefully() throws IOException {
        UserImportRow row = new UserImportRow();
        row.setRowNumber(3);
        row.setErrorReason("Some error");


        byte[] bytes = service.generateErrorExcel(List.of(row));

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void toCreateRequest_validRow_mapsAllFields() {
        UserImportRow row = new UserImportRow();
        row.setFullName("John Doe");
        row.setUsername("john.doe");
        row.setEmail("john@cresensolutions.com");
        row.setPassword("Pass@1234");
        row.setRole("employee");
        row.setManagerUsername("manager1");
        row.setGender("Male");
        row.setActive("true");

        CreateUserRequest req = service.toCreateRequest(row, "admin");

        assertThat(req.actorUsername()).isEqualTo("admin");
        assertThat(req.fullName()).isEqualTo("John Doe");
        assertThat(req.username()).isEqualTo("john.doe");
        assertThat(req.email()).isEqualTo("john@cresensolutions.com");
        assertThat(req.role()).isEqualTo("EMPLOYEE");
        assertThat(req.managerUsername()).isEqualTo("manager1");
        assertThat(req.active()).isTrue();
        assertThat(req.gender()).isEqualTo("Male");
       
        String decoded = new String(Base64.getDecoder().decode(req.password()), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("Pass@1234");
    }

    @Test
    void toCreateRequest_blankManagerUsername_mapsToNull() {
        UserImportRow row = new UserImportRow();
        row.setFullName("John Doe");
        row.setUsername("john.doe");
        row.setEmail("john@cresensolutions.com");
        row.setPassword("Pass@1234");
        row.setRole("EMPLOYEE");
        row.setManagerUsername("");
        row.setGender("Male");
        row.setActive("false");

        CreateUserRequest req = service.toCreateRequest(row, "admin");

        assertThat(req.managerUsername()).isNull();
        assertThat(req.active()).isFalse();
    }

    @Test
    void toCreateRequest_activeTrue_parsedCorrectly() {
        UserImportRow row = new UserImportRow();
        row.setFullName("Jane");
        row.setUsername("jane.doe");
        row.setEmail("jane@cresensolutions.com");
        row.setPassword("Pass@1234");
        row.setRole("MANAGER");
        row.setManagerUsername("");
        row.setGender("Female");
        row.setActive("true");

        CreateUserRequest req = service.toCreateRequest(row, "admin");

        assertThat(req.active()).isTrue();
    }

  
    private MockMultipartFile buildExcelFile(String[][] dataRows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.createSheet("LCR - Local Customer Repo");

         
            sheet.createRow(0).createCell(0).setCellValue("Banner");
            Row header = sheet.createRow(1);
            String[] headers = { "Full Name*", "Username*", "Email*", "Password*", "Role*", "Manager Username", "Gender*", "Active*" };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 2);
                for (int c = 0; c < dataRows[r].length; c++) {
                    row.createCell(c).setCellValue(dataRows[r][c]);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "users.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }


    private MockMultipartFile buildExcelFileWithNotesRow() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.createSheet("LCR - Local Customer Repo");
            sheet.createRow(0).createCell(0).setCellValue("Banner");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("Full Name*");
          
            Row notesRow = sheet.createRow(2);
            notesRow.createCell(0).setCellValue("Required. Max 255 chars.");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "users.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
