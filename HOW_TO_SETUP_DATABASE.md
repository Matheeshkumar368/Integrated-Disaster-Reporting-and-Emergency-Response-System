# How to Setup Database in MySQL Workbench

## Step 1: Open MySQL Workbench
1. Launch MySQL Workbench
2. Connect to your MySQL server (localhost or your server)

## Step 2: Delete Existing Database (Optional - if exists)
Run this command in MySQL Workbench query tab:

```sql
DROP DATABASE IF EXISTS disaster_management;
```

## Step 3: Run DATABASE_SETUP.sql
1. Go to File → Open SQL Script
2. Navigate to: `d:\kiro\demo\DATABASE_SETUP.sql`
3. Click "Open"
4. Click the "Execute" button (lightning bolt icon) or press Ctrl+Enter

## Alternative: Copy-Paste Method
1. Open `DATABASE_SETUP.sql` in any text editor (VS Code, Notepad++)
2. Copy all the content
3. In MySQL Workbench, paste into the query editor
4. Execute (Ctrl+Enter)

## Step 4: Verify Setup
After running, you should see:
- 5 users created
- 8 alerts created
- 10 disasters created

## Login Credentials:
| Role | Email | Password |
|------|-------|----------|
| Admin | admin@disaster.gov.in | admin123 |
| Responder | responder@disaster.gov.in | responder123 |
| Citizen | citizen@gmail.com | citizen123 |

## Step 5: Start Application
Run your Spring Boot application:
```bash
cd d:\kiro\demo
mvnw spring-boot:run
```

Then open browser: http://localhost:8080
