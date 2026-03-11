---
name: xlsx
description: "Use this skill any time a spreadsheet file is the primary input or output. This means any task where the user wants to: open, read, edit, or fix an existing .xlsx, .xlsm, .csv, or .tsv file (e.g., adding columns, computing formulas, formatting, charting, cleaning messy data); create a new spreadsheet from scratch or from other data sources; or convert between tabular file formats. Trigger especially when the user references a spreadsheet file by name or path — even casually (like \"the xlsx in my downloads\") — and wants something done to it or produced from it. Also trigger for cleaning or restructuring messy tabular data files (malformed rows, misplaced headers, junk data) into proper spreadsheets. The deliverable must be a spreadsheet file. Do NOT trigger when the primary deliverable is a Word document, HTML report, standalone Python script, database pipeline, or Google Sheets API integration, even if tabular data is involved."
license: Proprietary. LICENSE.txt has complete terms
allowed-tools: [create_xlsx, read_xlsx, read_file, write_file]
---

# Requirements for Outputs

## All Excel files

### Professional Font
- Use a consistent, professional font (e.g., Arial, Times New Roman) for all deliverables unless otherwise instructed by the user

### Zero Formula Errors
- Every Excel model MUST be delivered with ZERO formula errors (#REF!, #DIV/0!, #VALUE!, #N/A, #NAME?)

### Preserve Existing Templates (when updating templates)
- Study and EXACTLY match existing format, style, and conventions when modifying files
- Never impose standardized formatting on files with established patterns
- Existing template conventions ALWAYS override these guidelines

## Financial models

### Color Coding Standards
Unless otherwise stated by the user or existing template

#### Industry-Standard Color Conventions
- **Blue text (RGB: 0,0,255)**: Hardcoded inputs, and numbers users will change for scenarios
- **Black text (RGB: 0,0,0)**: ALL formulas and calculations
- **Green text (RGB: 0,128,0)**: Links pulling from other worksheets within same workbook
- **Red text (RGB: 255,0,0)**: External links to other files
- **Yellow background (RGB: 255,255,0)**: Key assumptions needing attention or cells that need to be updated

### Number Formatting Standards

#### Required Format Rules
- **Years**: Format as text strings (e.g., "2024" not "2,024")
- **Currency**: Use $#,##0 format; ALWAYS specify units in headers ("Revenue ($mm)")
- **Zeros**: Use number formatting to make all zeros "-", including percentages (e.g., "$#,##0;($#,##0);-")
- **Percentages**: Default to 0.0% format (one decimal)
- **Multiples**: Format as 0.0x for valuation multiples (EV/EBITDA, P/E)
- **Negative numbers**: Use parentheses (123) not minus -123

### Formula Construction Rules

#### Assumptions Placement
- Place ALL assumptions (growth rates, margins, multiples, etc.) in separate assumption cells
- Use cell references instead of hardcoded values in formulas
- Example: Use =B5*(1+$B$6) instead of =B5*1.05

#### Formula Error Prevention
- Verify all cell references are correct
- Check for off-by-one errors in ranges
- Ensure consistent formulas across all projection periods
- Test with edge cases (zero values, negative numbers)
- Verify no unintended circular references

#### Documentation Requirements for Hardcodes
- Comment or in cells beside (if end of table). Format: "Source: [System/Document], [Date], [Specific Reference], [URL if applicable]"
- Examples:
  - "Source: Company 10-K, FY2024, Page 45, Revenue Note, [SEC EDGAR URL]"
  - "Source: Company 10-Q, Q2 2025, Exhibit 99.1, [SEC EDGAR URL]"
  - "Source: Bloomberg Terminal, 8/15/2025, AAPL US Equity"
  - "Source: FactSet, 8/20/2025, Consensus Estimates Screen"

# XLSX creation, editing, and analysis

## XLSX Creation

Use the `create_xlsx` tool. Provide the full workbook as a JSON spec.

### Cell definition properties
- `value` — string, number, or boolean
- `formula` — Excel formula (e.g. `"SUM(A1:A10)"`)
- `format` — number format (e.g. `"$#,##0"`, `"0.0%"`, `"#,##0;(#,##0);-"`)
- `bold`, `italic` — boolean
- `bg_color` — 6-digit hex string (no #) — fills cell background
- `fg_color` — 6-digit hex string — sets font color
- `align` — `"left"` | `"center"` | `"right"`
- `border` — boolean — thin border all sides
- `colspan`, `rowspan` — integer — merge cells

### Color conventions (apply always unless template overrides)
- Blue text `"fg_color": "0000FF"` — hardcoded inputs
- Black text — formulas (default, no fg_color needed)
- Green text `"fg_color": "008000"` — cross-sheet links
- Red text `"fg_color": "FF0000"` — external file links
- Yellow background `"bg_color": "FFFF00"` — assumptions cells

### Example call
```json
{
  "path": "/workspace/model.xlsx",
  "sheets": [{
    "name": "Summary",
    "freeze_row": 1,
    "column_widths": [30, 15, 15, 15],
    "rows": [
      [{"value":"Item","bold":true,"bg_color":"4472C4","fg_color":"FFFFFF"},
       {"value":"FY2024","bold":true,"bg_color":"4472C4","fg_color":"FFFFFF"}],
      [{"value":"Revenue"},{"value":1000000,"format":"$#,##0"}],
      [{"value":"Growth","bold":true},{"formula":"(B3-B2)/B2","format":"0.0%"}]
    ]
  }]
}
```

## XLSX Reading and Analysis

Use `read_xlsx` to read an existing file before editing.

```json
{"path": "/workspace/data.xlsx", "sheet": "Sheet1"}
```

Returns `{"sheets": [...], "data": {"Sheet1": [[...], ...]}}`.

## Editing Existing Files

1. `read_xlsx` to understand current structure
2. Rebuild full workbook with `create_xlsx` incorporating the changes
3. Write to same path to overwrite

## Validation Workflow

After `create_xlsx`:
- Verify zero formula errors in all cells
- Confirm column widths, freeze rows, number formats match spec
- Spot-check formula cell values against expected results