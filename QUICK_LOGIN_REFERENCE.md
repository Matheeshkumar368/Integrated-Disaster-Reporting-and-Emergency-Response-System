# 🚀 QUICK LOGIN REFERENCE CARD

## 📋 COPY-PASTE CREDENTIALS

### 🔴 ADMIN LOGIN
```
Email: admin@disaster.gov.in
Password: admin123
```
**Button:** RED "Admin Login"

---

### 🔵 RESPONDER LOGIN
```
Email: responder@disaster.gov.in
Password: responder123
```
**Button:** BLUE "Responder Login"

---

### 🟢 CITIZEN LOGIN
```
Email: citizen@gmail.com
Password: citizen123
```
**Button:** GREEN "Citizen Login"

---

## 🎯 QUICK TEST FLOW

### 1️⃣ Admin Sends Alert
```
URL: http://localhost:8080
Login: admin@disaster.gov.in / admin123
Action: Fill form → Click "Broadcast Alert to Citizens"
```

### 2️⃣ Citizen Receives Alert
```
URL: http://localhost:8080 (new tab)
Login: citizen@gmail.com / citizen123
Action: View alerts in dashboard
```

### 3️⃣ Responder Acknowledges
```
URL: http://localhost:8080 (new tab)
Login: responder@disaster.gov.in / responder123
Action: View rescue requests → Acknowledge
```

---

## 📊 ALL USERS IN DATABASE

| # | Email                      | Password      | Name                | Role      | Location       |
|---|----------------------------|---------------|---------------------|-----------|----------------|
| 1 | admin@disaster.gov.in      | admin123      | Admin User          | Admin     | Chennai        |
| 2 | responder@disaster.gov.in  | responder123  | Emergency Responder | Responder | Coimbatore     |
| 3 | responder2@disaster.gov.in | responder123  | Rescue Team Lead    | Responder | Tiruchirappalli|
| 4 | citizen@gmail.com          | citizen123    | John Citizen        | Citizen   | Madurai        |
| 5 | citizen2@gmail.com         | citizen123    | Jane Citizen        | Citizen   | Chennai        |

---

## ⚡ SUPER QUICK TEST

**Copy and paste these one by one:**

1. Open: `http://localhost:8080`
2. Admin email: `admin@disaster.gov.in`
3. Admin password: `admin123`
4. Create alert → Send
5. New tab: `http://localhost:8080`
6. Citizen email: `citizen@gmail.com`
7. Citizen password: `citizen123`
8. ✅ See the alert!

---

## 🎨 LOGIN BUTTON COLORS

- 🔴 **RED** = Admin
- 🔵 **BLUE** = Responder  
- 🟢 **GREEN** = Citizen

---

**Application:** http://localhost:8080  
**Status:** ✅ RUNNING
