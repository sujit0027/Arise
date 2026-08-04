# Arise App - Setup and Build Instructions

Humne aapke low-end device ke liye pure Android app ka setup complete kar diya hai. Kyunki hum GitHub Actions (Cloud Build) use kar rahe hain, aapko apne PC par koi heavy software download karne ki zaroorat nahi hai.

Apne phone ke liye `.apk` file generate karne ke liye niche diye gaye steps follow karein:

---

## Step 1: Create a GitHub Repository (Only once)
1. Apne web browser me [GitHub.com](https://github.com) par jayein aur log in karein (agar account nahi hai toh free register karein).
2. Top-right corner me `+` icon par click karke **New repository** select karein.
3. Repository name rakhein: `Arise_App`.
4. Isko **Public** ya **Private** select karein (Private selection is safe and recommended).
5. **NOTE:** Create repository button par click karein (Don't initialize with README, .gitignore, or license).

---

## Step 2: Push code to GitHub (From terminal)
Git installation complete hote hi, hum local terminal me repository initialize kar denge. Uske baad aapko bas command line me niche diye gaye commands run karne hain code push karne ke liye:

```bash
# 1. Local files to Git track list me dalein
git init
git add .
git commit -m "Initial commit of Arise App"

# 2. GitHub repository se connect karein (Replace <USERNAME> with your GitHub username)
git branch -M main
git remote add origin https://github.com/<USERNAME>/Arise_App.git

# 3. Code upload karein (Pehli baar push karne par GitHub username/auth token maang sakta hai)
git push -u origin main
```

---

## Step 3: Get your APK (From GitHub Actions)
Jaise hi code push hoga, GitHub automatic build start kar dega:
1. Apne GitHub repository link par jayein.
2. Top menu bar me **Actions** tab par click karein.
3. Aapko `Build Android APK` naam ka workflow chalta dikhega (yellow spinning icon).
4. Kuch hi minutes me build check green status (✓) ho jayega.
5. Us build run par click karein aur scroll-down karke **Artifacts** section me jayein.
6. Wahan se `arise-debug-apk` download karein aur unzip karke `.apk` phone me install kar lein!

---

## Step 4: Phone Permissions
App chalane ke baad settings screen par ye permissions grant karein:
1. **Draw Over Other Apps:** App blocker screen trigger karne ke liye.
2. **App Usage Access:** Pata chalane ke liye ki kaunsa app open ho raha hai.
3. **Notifications:** Background notification persistent rakhne ke liye.
