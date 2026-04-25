package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.common.UserConstants;
import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.UserImportRow;
import com.cresensolutions.userservice.validation.ValidationPatterns;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class UserExcelService {

    private static final int COL_FULL_NAME    = 0;
    private static final int COL_USERNAME     = 1;
    private static final int COL_EMAIL        = 2;
    private static final int COL_PASSWORD     = 3;
    private static final int COL_ROLE         = 4;
    private static final int COL_MANAGER      = 5;
    private static final int COL_GENDER       = 6;
    private static final int COL_ACTIVE       = 7;
    private static final int COL_ERROR_REASON = 8;
    private static final int TOTAL_DATA_COLS  = 8;

    private static final String[] HEADERS = {
        "Full Name*", "Username*", "Email*", "Password*",
        "Role*", "Manager Username", "Gender*", "Active*"
    };
    private static final int[]    COL_WIDTHS    = { 7000, 5500, 8000, 6000, 4000, 6000, 5500, 3500 };
    private static final String[] ROLE_OPTIONS   = { "MANAGER", "EMPLOYEE" };
    private static final String[] GENDER_OPTIONS = { "Male", "Female", "Other", "Prefer not to say" };
    private static final String[] ACTIVE_OPTIONS = { "true", "false" };
    private static final Set<String> VALID_GENDERS =
            Set.of("male", "female", "other", "prefer not to say");

    private static final Pattern EMAIL_PATTERN    = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile(ValidationPatterns.USERNAME_REGEX);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(ValidationPatterns.STRICT_PASSWORD_REGEX);


    public byte[] generateTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("LCR - Local Customer Repo");
            sheet.createFreezePane(0, 2);

            XSSFCellStyle bannerStyle = buildBannerStyle(wb);
            XSSFCellStyle headerStyle = buildHeaderStyle(wb);
            XSSFCellStyle sampleStyle = buildSampleStyle(wb);
            XSSFCellStyle noteStyle   = buildNoteStyle(wb);

            Row bannerRow = sheet.createRow(0);
            bannerRow.setHeightInPoints(26);
            Cell bannerCell = bannerRow.createCell(0);
            bannerCell.setCellValue("LCR — Local Customer Repository  |  User Import Staging Area  |  Fill rows below, then upload to sync with the central database");
            bannerCell.setCellStyle(bannerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            Row headerRow = sheet.createRow(1);
            headerRow.setHeightInPoints(22);
            IntStream.range(0, HEADERS.length).forEach(i -> {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, COL_WIDTHS[i]);
            });

            String[] sample = { "John Doe", "john.doe", "john@cresensolutions.com",
                                 "Pass@1234", "EMPLOYEE", "manager.user", "Male", "true" };
            Row sampleRow = sheet.createRow(2);
            sampleRow.setHeightInPoints(18);
            IntStream.range(0, sample.length).forEach(i -> {
                Cell cell = sampleRow.createCell(i);
                cell.setCellValue(sample[i]);
                cell.setCellStyle(sampleStyle);
            });

            String[] notes = {
                "Required. Max 255 chars.",
                "Required. 3-100 chars, letters/numbers/._-",
                "Required. Valid email format, max 200 chars.",
                "Required. Min 8 chars, upper+lower+digit+special, no spaces.",
                "Required. Select MANAGER or EMPLOYEE.",
                "Optional. Must be an existing manager username.",
                "Required. Select from dropdown.",
                "Required. Select true or false."
            };
            Row noteRow = sheet.createRow(3);
            noteRow.setHeightInPoints(30);
            IntStream.range(0, notes.length).forEach(i -> {
                Cell cell = noteRow.createCell(i);
                cell.setCellValue(notes[i]);
                cell.setCellStyle(noteStyle);
            });

            addDropdown(wb, sheet, ROLE_OPTIONS,   COL_ROLE,   2, 1000);
            addDropdown(wb, sheet, GENDER_OPTIONS, COL_GENDER, 2, 1000);
            addDropdown(wb, sheet, ACTIVE_OPTIONS, COL_ACTIVE, 2, 1000);

            addInstructionsSheet(wb);
            return toBytes(wb);
        }
    }


    public List<UserImportRow> parseAndValidate(MultipartFile file,
                                                Set<String> existingUsernames,
                                                Set<String> existingEmails,
                                                Set<String> managerUsernames) throws IOException {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Set<String> seenUsernames = new HashSet<>(existingUsernames);
            Set<String> seenEmails    = new HashSet<>(existingEmails);

            return IntStream.rangeClosed(2, sheet.getLastRowNum())
                    .mapToObj(sheet::getRow)
                    .filter(row -> row != null && !isRowEmpty(row))
                    .filter(row -> !looksLikeNotesRow(row))
                    .map(row -> {
                        UserImportRow ir = mapRow(row);
                        String error = validate(ir, seenUsernames, seenEmails, managerUsernames);
                        if (error != null) {
                            ir.setErrorReason(error);
                        } else {
                            seenUsernames.add(ir.getUsername().toLowerCase());
                            seenEmails.add(ir.getEmail().toLowerCase());
                        }
                        return ir;
                    })
                    .toList();
        }
    }


    public byte[] generateErrorExcel(List<UserImportRow> allRows) throws IOException {
        List<UserImportRow> errorRows = allRows.stream()
                .filter(r -> r.getErrorReason() != null)
                .toList();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("LCR - Fix & Re-upload");
            sheet.createFreezePane(0, 1);

            XSSFCellStyle headerStyle      = buildHeaderStyle(wb);
            XSSFCellStyle errorRowStyle    = buildErrorRowStyle(wb);
            XSSFCellStyle errorReasonStyle = buildErrorReasonStyle(wb);

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            IntStream.range(0, HEADERS.length).forEach(i -> {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, COL_WIDTHS[i]);
            });
            Cell errHdr = headerRow.createCell(COL_ERROR_REASON);
            errHdr.setCellValue("Error Reason — fix the row, delete this column before re-uploading");
            errHdr.setCellStyle(buildErrorHeaderStyle(wb));
            sheet.setColumnWidth(COL_ERROR_REASON, 18000);

            addDropdown(wb, sheet, ROLE_OPTIONS,   COL_ROLE,   1, errorRows.size() + 1);
            addDropdown(wb, sheet, GENDER_OPTIONS, COL_GENDER, 1, errorRows.size() + 1);
            addDropdown(wb, sheet, ACTIVE_OPTIONS, COL_ACTIVE, 1, errorRows.size() + 1);

            int[] rowIdx = { 1 };
            errorRows.forEach(r -> {
                Row row = sheet.createRow(rowIdx[0]++);
                row.setHeightInPoints(18);
                setCellValue(row, COL_FULL_NAME,    r.getFullName(),        errorRowStyle);
                setCellValue(row, COL_USERNAME,     r.getUsername(),        errorRowStyle);
                setCellValue(row, COL_EMAIL,        r.getEmail(),           errorRowStyle);
                setCellValue(row, COL_PASSWORD,     r.getPassword(),        errorRowStyle);
                setCellValue(row, COL_ROLE,         r.getRole(),            errorRowStyle);
                setCellValue(row, COL_MANAGER,      r.getManagerUsername(), errorRowStyle);
                setCellValue(row, COL_GENDER,       r.getGender(),          errorRowStyle);
                setCellValue(row, COL_ACTIVE,       r.getActive(),          errorRowStyle);
                setCellValue(row, COL_ERROR_REASON, r.getErrorReason(),     errorReasonStyle);
            });

            addErrorSummarySheet(wb, errorRows);
            return toBytes(wb);
        }
    }


    public CreateUserRequest toCreateRequest(UserImportRow row, String actorUsername) {
        String base64Password = Base64.getEncoder()
                .encodeToString(row.getPassword().getBytes(StandardCharsets.UTF_8));
        return new CreateUserRequest(
                actorUsername, null,
                row.getFullName(), row.getUsername(), row.getEmail(),
                base64Password,
                row.getRole().toUpperCase(),
                row.getManagerUsername().isBlank() ? null : row.getManagerUsername(),
                Boolean.parseBoolean(row.getActive()),
                row.getGender(),
                null, null, null
        );
    }


    private String validate(UserImportRow row, Set<String> seenUsernames,
                            Set<String> seenEmails, Set<String> managerUsernames) {
        String errors = Stream.of(
                validateFullName(row.getFullName()),
                validateUsername(row.getUsername(), seenUsernames),
                validateEmail(row.getEmail(), seenEmails),
                validatePassword(row.getPassword()),
                validateRole(row.getRole()),
                validateManager(row.getManagerUsername(), managerUsernames),
                validateGender(row.getGender()),
                validateActive(row.getActive())
        )
        .filter(Objects::nonNull)
        .collect(java.util.stream.Collectors.joining("; "));
        return errors.isEmpty() ? null : errors;
    }

    private String validateFullName(String v) {
        if (isBlank(v))       return "Full Name is required";
        if (v.length() > 255) return "Full Name must not exceed 255 characters";
        return null;
    }

    private String validateUsername(String v, Set<String> seen) {
        if (isBlank(v))                                 return "Username is required";
        if (!USERNAME_PATTERN.matcher(v).matches())     return "Username must be 3-100 chars, letters/numbers/._- only";
        if (seen.contains(v.toLowerCase()))             return "Username '" + v + "' already exists or is duplicated in this file";
        return null;
    }

    private String validateEmail(String v, Set<String> seen) {
        if (isBlank(v))                                 return "Email is required";
        if (!EMAIL_PATTERN.matcher(v).matches())        return "Invalid email format";
        if (v.length() > 200)                           return "Email must not exceed 200 characters";
        if (seen.contains(v.toLowerCase()))             return "Email '" + v + "' already exists or is duplicated in this file";
        return null;
    }

    private String validatePassword(String v) {
        if (isBlank(v))                                              return "Password is required";
        if (v.length() < ValidationPatterns.PASSWORD_MIN_LENGTH)     return "Password must be at least " + ValidationPatterns.PASSWORD_MIN_LENGTH + " characters";
        if (!PASSWORD_PATTERN.matcher(v).matches())                  return "Password must include uppercase, lowercase, digit, and special character with no spaces";
        return null;
    }

    private String validateRole(String v) {
        String role = v == null ? "" : v.toUpperCase();
        if (isBlank(role))                              return "Role is required";
        if (!role.equals(UserConstants.ROLE_MANAGER)
         && !role.equals(UserConstants.ROLE_EMPLOYEE))  return "Role must be MANAGER or EMPLOYEE";
        return null;
    }

    private String validateManager(String v, Set<String> managers) {
        if (!isBlank(v) && !managers.contains(v.toLowerCase())) return "Manager username '" + v + "' not found";
        return null;
    }

    private String validateGender(String v) {
        if (isBlank(v))                                return "Gender is required";
        if (!VALID_GENDERS.contains(v.toLowerCase())) return "Gender must be Male, Female, Other, or Prefer not to say";
        return null;
    }

    private String validateActive(String v) {
        if (isBlank(v))                                                          return "Active is required (true or false)";
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false"))         return "Active must be 'true' or 'false'";
        return null;
    }


    private UserImportRow mapRow(Row row) {
        UserImportRow ir = new UserImportRow();
        ir.setRowNumber(row.getRowNum() + 1);
        ir.setFullName(getCellString(row, COL_FULL_NAME));
        ir.setUsername(getCellString(row, COL_USERNAME));
        ir.setEmail(getCellString(row, COL_EMAIL));
        ir.setPassword(getCellString(row, COL_PASSWORD));
        ir.setRole(getCellString(row, COL_ROLE));
        ir.setManagerUsername(getCellString(row, COL_MANAGER));
        ir.setGender(getCellString(row, COL_GENDER));
        ir.setActive(getCellString(row, COL_ACTIVE));
        return ir;
    }


    private void addInstructionsSheet(XSSFWorkbook wb) {
        XSSFSheet sheet = wb.createSheet("Instructions");
        sheet.setColumnWidth(0, 800);
        sheet.setColumnWidth(1, 22000);

        XSSFCellStyle titleStyle = wb.createCellStyle();
        XSSFFont titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 13);
        titleStyle.setFont(titleFont);

        XSSFCellStyle bodyStyle = wb.createCellStyle();
        bodyStyle.setWrapText(true);

        String[][] lines = {
            { "T", "LCR — Local Customer Repository" },
            { "T", "User Import Instructions" },
            { "", "" },
            { "B", "This Excel file is your Local Customer Repository (LCR) —" },
            { "B", "a local staging area for user data before it is synced to the central database." },
            { "", "" },
            { "T", "How to use this file:" },
            { "B", "1. Fill in the 'LCR - Local Customer Repo' sheet starting from row 3." },
            { "B", "2. Row 1 is the LCR banner, Row 2 is the column header — do not modify them." },
            { "B", "3. Fields marked with * are required." },
            { "B", "4. Use the dropdowns for Role, Gender, and Active columns." },
            { "B", "5. Password: min 8 chars, uppercase + lowercase + digit + special character." },
            { "B", "6. Manager Username is optional but must match an existing manager in the system." },
            { "", "" },
            { "T", "LCR Flow: Load → Check → Resolve" },
            { "B", "LOAD   : Upload this filled Excel (your Local Customer Repository) to the system." },
            { "B", "CHECK  : The system validates every row against business rules and existing data." },
            { "B", "RESOLVE: If errors exist, an error file is downloaded. Fix the red rows and re-upload." },
            { "", "" },
            { "B", "Once all rows pass validation, they are committed to the central database." },
        };

        int[] idx = { 0 };
        Arrays.stream(lines).forEach(line -> {
            Row row = sheet.createRow(idx[0]++);
            row.setHeightInPoints(22);
            Cell cell = row.createCell(1);
            cell.setCellValue(line[1]);
            cell.setCellStyle("T".equals(line[0]) ? titleStyle : bodyStyle);
        });
    }


    private void addErrorSummarySheet(XSSFWorkbook wb, List<UserImportRow> errorRows) {
        XSSFSheet sheet = wb.createSheet("LCR - Error Summary");
        sheet.setColumnWidth(0, 3000);
        sheet.setColumnWidth(1, 6000);
        sheet.setColumnWidth(2, 20000);

        XSSFCellStyle headerStyle = buildHeaderStyle(wb);

        XSSFCellStyle bodyStyle = wb.createCellStyle();
        bodyStyle.setWrapText(true);
        bodyStyle.setBorderBottom(BorderStyle.THIN);
        bodyStyle.setBorderRight(BorderStyle.THIN);

        XSSFCellStyle redStyle = wb.createCellStyle();
        redStyle.setWrapText(true);
        XSSFFont redFont = wb.createFont();
        redFont.setColor(new XSSFColor(new byte[]{(byte)153, (byte)27, (byte)27}, null));
        redStyle.setFont(redFont);

        Row hdr = sheet.createRow(0);
        hdr.setHeightInPoints(20);
        setCellValue(hdr, 0, "Row #",    headerStyle);
        setCellValue(hdr, 1, "Username", headerStyle);
        setCellValue(hdr, 2, "Error(s)", headerStyle);

        int[] idx = { 1 };
        errorRows.forEach(r -> {
            Row row = sheet.createRow(idx[0]++);
            row.setHeightInPoints(30);
            setCellValue(row, 0, String.valueOf(r.getRowNumber()),         bodyStyle);
            setCellValue(row, 1, Objects.toString(r.getUsername(), ""),    bodyStyle);
            setCellValue(row, 2, Objects.toString(r.getErrorReason(), ""), redStyle);
        });
    }


    private XSSFCellStyle buildBannerStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)15, (byte)23, (byte)42}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private XSSFCellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)15, (byte)139, (byte)141}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.MEDIUM);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildSampleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)232, (byte)248, (byte)248}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private XSSFCellStyle buildNoteStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setItalic(true);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        f.setFontHeightInPoints((short) 9);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)248, (byte)250, (byte)252}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setWrapText(true);
        s.setBorderBottom(BorderStyle.DASHED);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildErrorRowStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)255, (byte)241, (byte)242}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private XSSFCellStyle buildErrorReasonStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)254, (byte)226, (byte)226}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setColor(new XSSFColor(new byte[]{(byte)153, (byte)27, (byte)27}, null));
        f.setBold(true);
        s.setFont(f);
        s.setWrapText(true);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.MEDIUM);
        s.setBorderRight(BorderStyle.THIN);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private XSSFCellStyle buildErrorHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)185, (byte)28, (byte)28}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.MEDIUM);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }


    private void addDropdown(XSSFWorkbook wb, XSSFSheet sheet, String[] options,
                             int col, int firstRow, int lastRow) {
        DataValidationHelper h = sheet.getDataValidationHelper();
        DataValidation dv = h.createValidation(
                h.createExplicitListConstraint(options),
                new CellRangeAddressList(firstRow, lastRow, col, col));
        dv.setShowErrorBox(true);
        dv.createErrorBox("Invalid value", "Please select a value from the dropdown list.");
        sheet.addValidationData(dv);
    }

    private boolean looksLikeNotesRow(Row row) {
        String v = getCellString(row, 0).toLowerCase();
        return v.startsWith("required") || v.startsWith("optional") || v.startsWith("lcr");
    }

    private boolean isRowEmpty(Row row) {
        return IntStream.range(0, TOTAL_DATA_COLS)
                .mapToObj(i -> row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .allMatch(c -> c == null || c.getCellType() == CellType.BLANK);
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    private boolean isBlank(String v) { return v == null || v.isBlank(); }

    private void setCellValue(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
