import os

def fix_indentation(path):
    with open(path, 'r') as f:
        lines = f.read().split('\n')
        
    for i in range(len(lines)):
        if i > 800 and "val transactions =" in lines[i] and i+1 < len(lines) and "listOf(" in lines[i+1]:
            j = i + 2
            while j < len(lines) and "TransactionDetails" in lines[j]:
                # add 4 spaces to indentation
                lines[j] = "    " + lines[j]
                j += 1
            if j < len(lines) and lines[j].strip() == ")":
                lines[j] = "    " + lines[j]

    with open(path, 'w') as f:
        f.write('\n'.join(lines))

if __name__ == "__main__":
    fix_indentation("/Users/prajw/StudioProjects/Finlight-Android/app/src/test/java/io/pm/finlight/ui/viewmodel/TimePeriodReportViewModelTest.kt")
