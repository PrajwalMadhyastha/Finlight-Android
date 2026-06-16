import re

file_path = "app/src/test/java/io/pm/finlight/data/DataExportServiceTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Add DAO mock
content = content.replace(
    "private val recurringPatternDao: RecurringPatternDao = mockk(relaxed = true)",
    "private val recurringPatternDao: RecurringPatternDao = mockk(relaxed = true)\n    private val goalTransactionLinkDao: GoalTransactionLinkDao = mockk(relaxed = true)"
)

# 2. Add to mock db
content = content.replace(
    "every { db.recurringPatternDao() } returns recurringPatternDao",
    "every { db.recurringPatternDao() } returns recurringPatternDao\n        every { db.goalTransactionLinkDao() } returns goalTransactionLinkDao"
)

# 3. Add to setupMockData
content = content.replace(
    "coEvery { recurringPatternDao.getAllPatterns() } returns emptyList()",
    "coEvery { recurringPatternDao.getAllPatterns() } returns emptyList()\n        coEvery { goalTransactionLinkDao.getAll() } returns emptyList()"
)

# 4. Add to AppDataBackup constructor in tests (multiple places)
content = re.sub(
    r"goals = emptyList\(\),\s*trips = emptyList\(\),",
    "goals = emptyList(),\n                    goalTransactionLinks = emptyList(),\n                    trips = emptyList(),",
    content
)

# 5. Add to restoreFromBackupSnapshot deleteAll mock
content = content.replace(
    "coJustRun { goalDao.deleteAll() }",
    "coJustRun { goalDao.deleteAll() }\n            coJustRun { goalTransactionLinkDao.deleteAll() }"
)

# 6. Add to restoreFromBackupSnapshot coVerifyOrder
content = content.replace(
    "goalDao.deleteAll()",
    "goalDao.deleteAll()\n                goalTransactionLinkDao.deleteAll()"
)

# 7. Add to exportToJsonString assertions
content = content.replace(
    "assertEquals(1, backupData.goals.size)",
    "assertEquals(1, backupData.goals.size)\n            assertEquals(0, backupData.goalTransactionLinks.size)"
)

with open(file_path, "w") as f:
    f.write(content)

