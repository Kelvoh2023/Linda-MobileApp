# Linda backend setup (Supabase, about 15 minutes, free tier)

What it gives you: phones send scam reports to a real database, the fraud team confirms numbers in the console, and every phone downloads the updated fraud list and warnings.
Phones sign in anonymously (no phone number, no email stored). The fraud team signs in with email and password.

## 1. Create the project
1. Go to supabase.com, sign in with GitHub, click **New project**.
2. Name it `linda`, pick a database password (save it), choose the region closest to Kenya (e.g. Frankfurt or London), click **Create**.
3. Wait about 2 minutes until the dashboard opens.

## 2. Create the tables and security rules
1. Left menu, **SQL Editor**, **New query**.
2. Paste the whole content of `backend/schema.sql`, click **Run**. You should see "Success".
   It is safe to run again later.

## 3. Allow phones to sign in anonymously
Left menu, **Authentication**, **Sign In / Providers**, switch **Allow anonymous sign-ins** ON, save.
(Optional but recommended: Authentication, Attack Protection, turn on CAPTCHA later if abused.)

## 4. Create the fraud team login
1. **Authentication**, **Users**, **Add user**, **Create new user**. Enter an email and password, tick **Auto Confirm User**.
2. Click the new user and copy its **User UID**.
3. **SQL Editor**, new query, run (replace the UID):
   `insert into public.staff (user_id, note) values ('PASTE-UID-HERE', 'Fraud analyst');`
Only users listed in `staff` can see all reports and confirm numbers. Everyone else is blocked by the database itself.

## 5. Connect the app
1. **Project Settings**, **API**. Copy the **Project URL** and the **anon public** key.
   Never copy the `service_role` key anywhere in the app.
2. On GitHub open `www/linda-config.js`, click the pencil, paste the two values between the quotes, commit.
3. The build runs by itself. Install the new APK on top of the old one.
   The console page (`dashboard.html`) now shows a sign-in form. Add `?demo=1` to the address to see the old demo console.

## How to check it works
1. Phone: open a scam message, tap Report. Settings should show "Connected. Updated ...".
2. Console: sign in. The number appears in the review queue. Click **Confirm and block**.
3. Phone: Settings, **Update now** (or wait a minute). The number is now flagged as a confirmed scam.
4. Supabase, Table Editor, `reports` and `numbers` show the data.

## Privacy by design
- Reports hold only: the number reported, scam type, county, and up to 300 characters of the message the person chose to report.
- Each phone sees only its own reports. The staff see all.
- One report per phone per number per day; 40 per hour per phone (blocks spam).
- Nothing is sent unless the person taps Report.
