# MiniCloud Migration Guide
## Web Application → Java Swing Desktop Application

This guide documents the complete migration from a web-based architecture to a pure Java Swing desktop application with MySQL Workbench database management.

---

## 🎯 Migration Overview

### OLD Architecture (Web-based)
```
Browser → localhost:8080 → Spring Boot → H2 Database
```

### NEW Architecture (Desktop Swing)
```
Java Swing GUI → Service Layer → MySQL (via JDBC/JPA)
```

---

## ✅ What Was Changed

### 1. **Database Migration: H2 → MySQL**
- ❌ **REMOVED**: H2 embedded database dependency
- ✅ **ADDED**: MySQL connector only
- ✅ **CREATED**: Complete MySQL Workbench setup script (`mysql-workbench-setup.sql`)
- ✅ **CONFIGURED**: `spring.jpa.hibernate.ddl-auto=validate` (no auto-schema generation)

### 2. **Application Mode: Web → Desktop**
- ❌ **REMOVED**: `spring-boot-starter-web` (no Tomcat server)
- ❌ **REMOVED**: `spring-boot-starter-websocket`
- ❌ **REMOVED**: `spring-boot-starter-security` (not needed for desktop)
- ❌ **REMOVED**: `spring-boot-starter-actuator`
- ❌ **REMOVED**: Swagger/OpenAPI dependencies
- ❌ **REMOVED**: Flyway database migrations
- ✅ **CONFIGURED**: `spring.main.web-application-type=none`

### 3. **Frontend: HTML/CSS/JS → Java Swing**
- ❌ **REMOVED**: All web controllers (@RestController)
- ❌ **REMOVED**: Static web resources (HTML, CSS, JavaScript)
- ✅ **CREATED**: `MainWindow.java` - Full Swing desktop UI
- ✅ **FEATURES**: 
  - Tabbed interface (S3, EC2, Lambda, RDS, IAM, Logs)
  - Integrated console output panel
  - AWS-styled dark theme support (FlatLaf)

### 4. **Configuration Updates**
- ✅ **UPDATED**: `application.properties` for MySQL-only connection
- ✅ **ADDED**: UTF-8 encoding configuration for Windows
- ✅ **DISABLED**: SQL init mode (no schema.sql/data.sql)
- ✅ **DISABLED**: Web resources and multipart uploads

---

## 📋 Step-by-Step Migration Checklist

### Phase 1: MySQL Database Setup

#### Step 1.1: Install MySQL Server
```bash
# Windows: Download MySQL Installer from mysql.com
# Or use Chocolatey:
choco install mysql

# Linux:
sudo apt-get install mysql-server

# macOS:
brew install mysql
```

#### Step 1.2: Start MySQL Service
```bash
# Windows:
net start MySQL80

# Linux:
sudo systemctl start mysql

# macOS:
brew services start mysql
```

#### Step 1.3: Run MySQL Workbench Setup Script
1. Open MySQL Workbench
2. Connect to `localhost` as `root`
3. Open the file: `minicloud/mysql-workbench-setup.sql`
4. Execute the entire script (Ctrl+Shift+Enter)
5. Verify tables were created:
   ```sql
   USE minicloud_db;
   SHOW TABLES;
   SELECT * FROM iam_users;
   ```

#### Step 1.4: Update Database Password
Edit `minicloud/minicloud-api/src/main/resources/application.properties`:
```properties
spring.datasource.password=YOUR_ACTUAL_MYSQL_ROOT_PASSWORD
```

---

### Phase 2: Project Configuration

#### Step 2.1: Clean Old Build Artifacts
```bash
cd minicloud
.\mvnw.cmd clean
```

#### Step 2.2: Verify pom.xml Changes
Check that `minicloud/minicloud-api/pom.xml` has:
- ✅ `mysql-connector-j` dependency
- ❌ NO `spring-boot-starter-web`
- ❌ NO `h2` database
- ❌ NO `spring-boot-starter-security`
- ✅ `flatlaf` for Swing UI

#### Step 2.3: Verify application.properties
Check `minicloud/minicloud-api/src/main/resources/application.properties`:
```properties
spring.main.web-application-type=none
spring.datasource.url=jdbc:mysql://localhost:3306/minicloud_db...
spring.jpa.hibernate.ddl-auto=validate
spring.sql.init.mode=never
```

---

### Phase 3: Build and Run

#### Step 3.1: Build the Project
```bash
cd minicloud
.\mvnw.cmd clean package -DskipTests
```

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: XX s
```

#### Step 3.2: Run the Desktop Application
```bash
.\mvnw.cmd spring-boot:run -pl minicloud-api
```

**OR** with explicit UTF-8 encoding:
```bash
.\mvnw.cmd spring-boot:run -pl minicloud-api -Dfile.encoding=UTF-8
```

#### Step 3.3: Verify Startup
You should see:
1. ✅ Spring Boot starts without errors
2. ✅ Database connection established
3. ✅ Swing window opens automatically
4. ✅ Console shows: "Application started - Spring Boot context initialized"
5. ✅ Status bar shows: "● Connected to minicloud_db @ localhost:3306"

---

## 🔧 Troubleshooting

### Problem: "Communications link failure"
**Cause:** MySQL service is not running

**Fix:**
```bash
# Windows
net start MySQL80

# Linux
sudo systemctl start mysql

# macOS
brew services start mysql
```

---

### Problem: "Access denied for user 'root'"
**Cause:** Wrong password in application.properties

**Fix:**
1. Reset MySQL root password:
   ```sql
   ALTER USER 'root'@'localhost' IDENTIFIED BY 'newpassword';
   FLUSH PRIVILEGES;
   ```
2. Update `application.properties`:
   ```properties
   spring.datasource.password=newpassword
   ```

---

### Problem: "Schema-validation: missing table [...]"
**Cause:** MySQL Workbench script was not run

**Fix:**
1. Open MySQL Workbench
2. Run the complete `mysql-workbench-setup.sql` script
3. Verify tables exist:
   ```sql
   USE minicloud_db;
   SHOW TABLES;
   ```

---

### Problem: Swing window doesn't open
**Cause:** MainWindow bean not found or headless mode enabled

**Fix:**
1. Verify `MiniCloudApiApplication.java` has:
   ```java
   System.setProperty("java.awt.headless", "false");
   ```
2. Verify `MainWindow.java` is annotated with `@Component`
3. Check package structure - MainWindow must be in `com.minicloud.api.ui` or sub-package

---

### Problem: "NoSuchBeanDefinitionException: No qualifying bean of type MainWindow"
**Cause:** Component scan not finding MainWindow

**Fix:**
Ensure MainWindow is in the correct package:
```
com.minicloud.api.MiniCloudApiApplication  ← main class
com.minicloud.api.ui.MainWindow            ← Swing window ✓
```

---

### Problem: UI freezes when clicking buttons
**Cause:** Database calls on Event Dispatch Thread

**Fix:**
Use `SwingWorker` for all database operations:
```java
loadBtn.addActionListener(e -> {
    new SwingWorker<List<Bucket>, Void>() {
        @Override
        protected List<Bucket> doInBackground() {
            return bucketService.getAllBuckets();
        }
        
        @Override
        protected void done() {
            try {
                List<Bucket> buckets = get();
                // Update table model here
            } catch (Exception ex) {
                mainWindow.log("Error: " + ex.getMessage());
            }
        }
    }.execute();
});
```

---

### Problem: "LazyInitializationException"
**Cause:** Missing `@Transactional` on service methods

**Fix:**
Add `@Transactional` to all service methods:
```java
@Service
public class BucketService {
    @Transactional(readOnly = true)
    public List<Bucket> getAllBuckets() {
        return bucketRepository.findAll();
    }
}
```

---

### Problem: Maven wrapper not found
**Cause:** `mvnw.cmd` not in repository

**Fix:**
Use system Maven instead:
```bash
mvn clean package -DskipTests
mvn spring-boot:run -pl minicloud-api
```

---

## 📁 New Project Structure

```
minicloud/
├── mysql-workbench-setup.sql           ← NEW: Complete MySQL schema
├── MIGRATION_GUIDE.md                  ← NEW: This file
├── minicloud-api/
│   ├── pom.xml                         ← UPDATED: Removed web dependencies
│   ├── src/main/
│   │   ├── java/com/minicloud/api/
│   │   │   ├── MiniCloudApiApplication.java  ← UPDATED: Launches Swing
│   │   │   ├── ui/
│   │   │   │   └── MainWindow.java     ← NEW: Swing desktop UI
│   │   │   ├── domain/                 ← KEPT: JPA entities
│   │   │   ├── service/                ← KEPT: Business logic
│   │   │   └── (removed: controllers, security, config)
│   │   └── resources/
│   │       ├── application.properties  ← UPDATED: MySQL only
│   │       └── (removed: static/, templates/, db/migration/)
│   └── minicloud-data/                 ← CREATED: Runtime data
│       ├── db/
│       ├── storage/
│       ├── lambda-tmp/
│       ├── logs/
│       └── rds/
```

---

## 🎨 UI Features

### Main Window Components

1. **Header Bar**
   - Title: "☁ Mini-AWS Management Console"
   - AWS-styled dark theme

2. **Tabbed Panels**
   - 🪣 S3 Buckets
   - 💻 EC2 Instances
   - ⚡ Lambda Functions
   - 🗄️ RDS Databases
   - 👤 IAM Users
   - 📋 Activity Logs

3. **Console Output**
   - Real-time logging
   - Timestamp for each message
   - Clear console button
   - Dark terminal theme

4. **Status Bar**
   - Database connection status
   - Green indicator when connected

---

## 🔐 Security Notes

### What Was Removed
- ❌ Spring Security (not needed for desktop app)
- ❌ JWT authentication (no HTTP endpoints)
- ❌ CORS configuration (no browser)
- ❌ CSRF protection (no web forms)

### What Remains
- ✅ BCrypt password hashing in database
- ✅ Database-level foreign key constraints
- ✅ Input validation via Bean Validation

### Future Considerations
If you need authentication in the desktop app:
1. Create a login dialog on startup
2. Store authenticated user in application context
3. Pass user context to service methods
4. No need for JWT tokens (single-user desktop app)

---

## 📊 Database Schema

All tables are created in MySQL Workbench. Key tables:

### IAM Tables
- `iam_users` - User accounts
- `iam_policies` - Access policies
- `iam_access_keys` - API access keys

### Storage Tables
- `iam_buckets` - S3-like buckets
- `storage_objects` - Files in buckets
- `storage_object_metadata` - File metadata

### Compute Tables
- `compute_instances` - EC2-like instances
- `compute_security_groups` - Security groups
- `compute_security_group_rules` - Firewall rules

### Lambda Tables
- `lambda_functions` - Serverless functions
- `lambda_invocation_logs` - Execution logs

### RDS Tables
- `rds_instances` - Database instances

### Monitoring Tables
- `monitoring_alarms` - CloudWatch-like alarms
- `monitoring_audit_logs` - CloudTrail-like audit log

### Billing Tables
- `billing_records` - Cost tracking
- `billing_invoices` - Monthly invoices

---

## 🚀 Next Steps

### 1. Wire Real Data to UI Tables
Currently, buttons only log messages. To load real data:

```java
// In MainWindow.java, inject services:
@Autowired
private BucketService bucketService;

// In buildBucketsPanel(), update loadBtn:
loadBtn.addActionListener(e -> {
    log("[S3] Loading buckets...");
    new SwingWorker<List<Bucket>, Void>() {
        @Override
        protected List<Bucket> doInBackground() {
            return bucketService.getAllBuckets();
        }
        
        @Override
        protected void done() {
            try {
                List<Bucket> buckets = get();
                DefaultTableModel model = (DefaultTableModel) table.getModel();
                model.setRowCount(0);
                for (Bucket b : buckets) {
                    model.addRow(new Object[]{
                        b.getId(), b.getName(), b.getOwnerId(),
                        b.getRegion(), b.getCreatedAt()
                    });
                }
                log("[S3] Loaded " + buckets.size() + " buckets");
            } catch (Exception ex) {
                log("[ERROR] " + ex.getMessage());
            }
        }
    }.execute();
});
```

### 2. Add Create/Edit Dialogs
Create modal dialogs for adding/editing resources:
```java
JDialog createDialog = new JDialog(this, "Create Bucket", true);
// Add form fields, OK/Cancel buttons
```

### 3. Add Real-time Updates
Use Spring's `@Scheduled` to refresh data periodically:
```java
@Scheduled(fixedRate = 30000)
public void refreshInstances() {
    SwingUtilities.invokeLater(() -> {
        // Refresh EC2 instances table
    });
}
```

### 4. Add Error Handling
Show user-friendly error dialogs:
```java
JOptionPane.showMessageDialog(this,
    "Failed to load buckets: " + ex.getMessage(),
    "Error",
    JOptionPane.ERROR_MESSAGE);
```

---

## 📝 Default Credentials

After running the MySQL setup script, you can use:

| Field | Value |
|-------|-------|
| Username | `admin` |
| Email | `admin@minicloud.io` |
| Password | `admin123` (BCrypt hashed) |
| Account ID | `123456789012` |
| Role | `ADMIN` |

---

## ✨ Summary

You have successfully migrated from:
- ❌ Web-based architecture with browser UI
- ❌ H2 embedded database with auto-schema generation
- ❌ REST API with Spring Security

To:
- ✅ Pure Java Swing desktop application
- ✅ MySQL database with Workbench-managed schema
- ✅ Direct service layer access (no HTTP)

**No localhost. No browser. Pure desktop Java application.**

---

## 📞 Support

If you encounter issues not covered in this guide:
1. Check the console output in the Swing window
2. Check application logs in `minicloud-data/logs/app.log`
3. Verify MySQL connection with MySQL Workbench
4. Ensure all dependencies are in `pom.xml`
5. Rebuild with `mvnw clean package -DskipTests`

---

**Migration completed successfully! 🎉**
