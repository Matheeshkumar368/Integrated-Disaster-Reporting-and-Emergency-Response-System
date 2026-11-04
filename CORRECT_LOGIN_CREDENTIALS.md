# 🔐 CORRECT LOGIN CREDENTIALS - From Your Database

## ✅ ACTUAL USERS IN YOUR DATABASE

Based on your MySQL database screenshot and the DataInitializer code, here are the CORRECT credentials:

---

## 👨‍💼 ADMIN LOGIN

**Email:** `admin@disaster.gov.in`  
**Password:** `admin123`  
**Role:** Admin  
**Name:** Admin User  
**Phone:** 9876543210  
**Location:** Chennai

### Admin Can:
- ✅ Broadcast alerts to all citizens
- ✅ View all disasters on map
- ✅ Manage alerts (create, broadcast, resolve)
- ✅ View statistics and reports
- ✅ Access admin dashboard

---

## 🚑 RESPONDER LOGIN

### Responder 1:
**Email:** `responder@disaster.gov.in`  
**Password:** `responder123`  
**Role:** Responder  
**Name:** Emergency Responder  
**Phone:** 9876543211  
**Location:** Coimbatore

### Responder 2:
**Email:** `responder2@disaster.gov.in`  
**Password:** `responder123`  
**Role:** Responder  
**Name:** Rescue Team Lead  
**Phone:** 9876543214  
**Location:** Tiruchirappalli

### Responders Can:
- ✅ View rescue requests from citizens
- ✅ Acknowledge disaster reports
- ✅ Send notifications to citizens
- ✅ View disaster photos
- ✅ Access responder dashboard

---

## 👥 CITIZEN LOGIN

### Citizen 1:
**Email:** `citizen@gmail.com`  
**Password:** `citizen123`  
**Role:** Citizen  
**Name:** John Citizen  
**Phone:** 9876543212  
**Location:** Madurai

### Citizen 2:
**Email:** `citizen2@gmail.com`  
**Password:** `citizen123`  
**Role:** Citizen  
**Name:** Jane Citizen  
**Phone:** 9876543213  
**Location:** Chennai

### Citizens Can:
- ✅ View broadcasted alerts
- ✅ Report disasters with camera
- ✅ Upload disaster photos/videos
- ✅ Receive notifications from responders
- ✅ View disaster map

---

## 🧪 TESTING INSTRUCTIONS

### Test 1: Admin Broadcasts Alert
1. Go to: http://localhost:8080
2. Click **RED "Admin Login"** button
3. Enter:
   - Email: `admin@disaster.gov.in`
   - Password: `admin123`
4. Fill broadcast form and send alert

### Test 2: Citizen Receives Alert
1. Open new browser tab
2. Go to: http://localhost:8080
3. Click **GREEN "Citizen Login"** button
4. Enter:
   - Email: `citizen@gmail.com`
   - Password: `citizen123`
5. You should see the alert!

### Test 3: Responder Acknowledges
1. Open new browser tab
2. Go to: http://localhost:8080
3. Click **BLUE "Responder Login"** button
4. Enter:
   - Email: `responder@disaster.gov.in`
   - Password: `responder123`
5. View rescue requests and acknowledge

---

## 📊 DATABASE INFORMATION

**Database Name:** `disaster_management`  
**Host:** `localhost:3306`  
**Total Users:** 5

### Users Table Structure:
```
┌────┬──────────────────────────────┬──────────────┬──────────────────────┬──────────────┬──────────┬────────────────┐
│ id │ email                        │ password     │ name                 │ phone        │ location │ role           │
├────┼──────────────────────────────┼──────────────┼──────────────────────┼──────────────┼──────────┼────────────────┤
│ 1  │ admin@disaster.gov.in        │ [BCrypt Hash]│ Admin User           │ 9876543210   │ Chennai  │ Admin          │
│ 2  │ responder@disaster.gov.in    │ [BCrypt Hash]│ Emergency Responder  │ 9876543211   │ Coimbat. │ Responder      │
│ 3  │ responder2@disaster.gov.in   │ [BCrypt Hash]│ Rescue Team Lead     │ 9876543214   │ Tiruchi. │ Responder      │
│ 4  │ citizen@gmail.com            │ [BCrypt Hash]│ John Citizen         │ 9876543212   │ Madurai  │ Citizen        │
│ 5  │ citizen2@gmail.com           │ [BCrypt Hash]│ Jane Citizen         │ 9876543213   │ Chennai  │ Citizen        │
└────┴──────────────────────────────┴──────────────┴──────────────────────┴──────────────┴──────────┴────────────────┘
```

---

## ⚠️ IMPORTANT NOTES

1. **Email Format:**
   - Admin & Responders: `@disaster.gov.in`
   - Citizens: `@gmail.com`

2. **Passwords:**
   - All passwords are stored as BCrypt hashes in database
   - Plain text passwords only work during login (they are hashed and compared)

3. **Role-Based Access:**
   - System checks role from JWT token
   - Wrong role = Access Denied

4. **Database Reset:**
   - Every time application starts, it clears and recreates sample data
   - This is for development/testing only

---

## 🔄 IF YOU WANT TO ADD NEW USERS

Edit: `src/main/java/com/disaster/management/DataInitializer.java`

Add new user:
```java
User newUser = new User();
newUser.setEmail("newuser@example.com");
newUser.setPassword(passwordEncoder.encode("password123"));
newUser.setName("New User Name");
newUser.setPhone("9876543215");
newUser.setLocation("City");
newUser.setRole("Citizen"); // or "Admin" or "Responder"
userRepo.save(newUser);
```

Then restart application.

---

## ✅ QUICK REFERENCE

| Role      | Email                      | Password      | Button Color |
|-----------|----------------------------|---------------|--------------|
| Admin     | admin@disaster.gov.in      | admin123      | RED          |
| Responder | responder@disaster.gov.in  | responder123  | BLUE         |
| Citizen   | citizen@gmail.com          | citizen123    | GREEN        |

---

**Application URL:** http://localhost:8080  
**Status:** ✅ RUNNING  
**Ready to test!** 🚀
