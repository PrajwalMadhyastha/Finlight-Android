import os
import re

def fix_reports_view_model(path):
    with open(path, 'r') as f:
        lines = f.read().split('\n')
    
    methods_to_fix = [
        "fun `reportData returns null percentageChange when previous summary expenses is zero`() = runTest {",
        "fun `reportData returns null percentageChange when previous summary is null`() = runTest {"
    ]
    
    for i in range(len(lines)):
        for method in methods_to_fix:
            if lines[i].strip() == method:
                lines[i] = "    " + method.replace("= runTest {", "=\n        runTest {")
                # Indent lines until }
                j = i + 1
                while j < len(lines):
                    if lines[j].startswith("    }"):
                        lines[j] = "        }"
                        break
                    if lines[j].strip() == "":
                        lines[j] = ""
                    else:
                        lines[j] = "    " + lines[j]
                    j += 1
                
    with open(path, 'w') as f:
        f.write('\n'.join(lines))


def fix_time_period_view_model(path):
    with open(path, 'r') as f:
        lines = f.read().split('\n')
    
    methods_to_fix = [
        "fun `yearlyMonthlyBreakdown handles income and expense exclusions correctly`() = runTest {",
        "fun `totalIncome and totalExpenses respect exclusions in YEARLY time period`() = runTest {",
        "fun `monthlyAverageIncome and monthlyAverageExpenses omit excluded months`() = runTest {",
        "fun `toggleIncomeExclusion calls repository toggle`() = runTest {",
        "fun `toggleExpenseExclusion calls repository toggle`() = runTest {"
    ]
    
    for i in range(len(lines)):
        for method in methods_to_fix:
            if lines[i].strip() == method:
                lines[i] = "    " + method.replace("= runTest {", "=\n        runTest {")
                j = i + 1
                while j < len(lines):
                    if lines[j].startswith("    }"):
                        lines[j] = "        }"
                        break
                    if lines[j].strip() == "":
                        lines[j] = ""
                    elif lines[j].startswith("        "):
                        lines[j] = "    " + lines[j]
                    j += 1
                    
        # Remove trailing spaces from empty lines (lines 866, 913, 916, 925, 928 and general cleanup)
        if lines[i].strip() == "" and len(lines[i]) > 0:
            lines[i] = ""

        if "val transactions = listOf(" in lines[i]:
            lines[i] = lines[i].replace("val transactions = listOf(", "val transactions =\n                listOf(")
            j = i + 1
            while j < len(lines) and ")" not in lines[j] and "TransactionDetails" in lines[j]:
                lines[j] = "    " + lines[j]
                j += 1
            if j < len(lines) and lines[j].strip() == ")":
                lines[j] = "                )"

    with open(path, 'w') as f:
        f.write('\n'.join(lines))


def fix_format_utils(path):
    with open(path, 'r') as f:
        lines = f.read().split('\n')
        
    # Line 12: Class body should not start with blank line
    for i in range(len(lines)):
        if "class FormatUtilsTest {" in lines[i]:
            if lines[i+1].strip() == "":
                del lines[i+1]
                break
                
    # 22, 31, etc: A multiline expression should start on a new line
    for i in range(len(lines)):
        if "val cal = Calendar.getInstance().apply {" in lines[i]:
            lines[i] = "        val cal =\n            Calendar.getInstance().apply {"
            # Also indent the next lines until }
            j = i + 1
            while j < len(lines):
                if lines[j].strip() == "}":
                    lines[j] = "            }"
                    break
                lines[j] = "    " + lines[j]
                j += 1

    with open(path, 'w') as f:
        f.write('\n'.join(lines))

if __name__ == "__main__":
    base = "/Users/prajw/StudioProjects/Finlight-Android/app/src/test/java/io/pm/finlight"
    fix_reports_view_model(base + "/ui/viewmodel/ReportsViewModelTest.kt")
    fix_time_period_view_model(base + "/ui/viewmodel/TimePeriodReportViewModelTest.kt")
    fix_format_utils(base + "/utils/FormatUtilsTest.kt")
