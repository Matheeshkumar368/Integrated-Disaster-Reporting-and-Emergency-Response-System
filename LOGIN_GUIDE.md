# 🔐 LOGIN GUIDE - APPLICATION READY!

## ✅ APPLICATION STATUS

**URL:** http://localhost:8080  
**Status:** ✅ RUNNING  
**Port:** 8080  
**Process ID:** 21032  
**Database:** ✅ CONNECTED  
**Users:** 5 loaded  
**Alerts:** 12 loaded  
**Disasters:** 20 loaded

---

## 👥 LOGIN CREDENTIALS

### 🔴 ADMIN
```
Email: admin@disaster.gov.in
Password: admin123
```
**Access:** Full system control, broadcast alerts, view reports

### 🔵 RESPONDER
```
Email: responder@disaster.gov.in
Password: responder123
```
**Access:** View rescue requests, acknowledge citizen reports

### 🟢 CITIZEN
```
Email: citizen@gmail.com
Password: citizen123
```
**Access:** View alerts, report disasters with camera

---

## 🧪 HOW TO LOGIN

### Step 1: Open Browser
Go to: **http://localhost:8080**

### Step 2: Select Role
Click the colored button for your role:
- **RED** = Admin
- **BLUE** = Responder
- **GREEN** = Citizen

### Step 3: Enter Credentials
Copy and paste the email and password from above

### Step 4: Click Login
You'll be redirected to your dashboard

---

## 🔍 IF LOGIN FAILS

### Check 1: Application Running?
```powershell
Get-NetTCPConnection -LocalPort 8080
```
Should show: `State: Listen`

### Check 2: Clear Browser Cache
```
Ctrl + Shift + Delete
Clear cached files
Close all tabs
Reopen browser
```

### Check 3: Check Console
1. Press **F12**
2. Go to **Console** tab
3. Look for errors
4. Try login again

### Check 4: Verify Database
The application logs show:
```
=== Sample Data Inserted Successfully ===
Users: 5
Alerts: 12
Disasters: 20
```
This means database is working!

---

## 📊 WHAT YOU CAN DO

### As Admin:
1. ✅ Broadcast alerts to citizens
2. ✅ View all alerts in card format
3. ✅ Acknowledge/Resolve alerts
4. ✅ View statistics
5. ✅ Access reports page

### As Citizen:
1. ✅ View broadcasted alerts
2. ✅ Report disasters with camera
3. ✅ Capture photos (front/back camera)
4. ✅ Send reports to responders
5. ✅ Add message and location

### As Responder:
1. ✅ View citizen rescue requests
2. ✅ See photos from citizens
3. ✅ Acknowledge reports
4. ✅ Send notifications to citizens

---

## 🎯 QUICK TEST

### Test 1: Admin Login
1. Go to http://localhost:8080
2. Click RED "Admin Login"
3. Email: `admin@disaster.gov.in`
4. Password: `admin123`
5. ✅ Should see admin dashboard

### Test 2: Citizen Login
1. Go to http://localhost:8080
2. Click GREEN "Citizen Login"
3. Email: `citizen@gmail.com`
4. Password: `citizen123`
5. ✅ Should see citizen dashboard

### Test 3: Responder Login
1. Go to http://localhost:8080
2. Click BLUE "Responder Login"
3. Email: `responder@disaster.gov.in`
4. Password: `responder123`
5. ✅ Should see responder dashboard

---

## ⚠️ IMPORTANT NOTES

### Email Format:
- Admin & Responders: `@disaster.gov.in`
- Citizens: `@gmail.com`

### Passwords:
- All passwords are stored as BCrypt hashes
- Plain text passwords only work during login
- They are hashed and compared with database

### Database:
- ✅ MySQL connected successfully
- ✅ All tables created
- ✅ Sample data loaded
- ✅ Ready to use

---

## 🚀 READY TO USE!

**Everything is working!**

1. ✅ Application running
2. ✅ Database connected
3. ✅ Users loaded
4. ✅ Ready for login

**Just open http://localhost:8080 and login!** 🎉

---

## 📞 ALL CREDENTIALS

| Role      | Email                      | Password      | Button |
|-----------|----------------------------|---------------|--------|
| Admin     | admin@disaster.gov.in      | admin123      | RED    |
| Responder | responder@disaster.gov.in  | responder123  | BLUE   |
| Citizen   | citizen@gmail.com          | citizen123    | GREEN  |

**Copy these credentials and try logging in!**
