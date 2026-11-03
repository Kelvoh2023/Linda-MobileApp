/* Linda scam model: a small naive Bayes text classifier plus plain-language rules.
   The training messages below are written by us (synthetic), in English, Swahili and Sheng.
   They are NOT Safaricom data. In production this would be trained on confirmed reports. */
var LindaModel = (function () {
  var S = 1, L = 0;
  var DATA = [
    /* ---- scam: English ---- */
    [S, "Confirmed. You have received Ksh 8,500.00 from JOHN KAMAU 0711000412. Sorry I sent it to the wrong number, please reverse it urgently."],
    [S, "Hello I mistakenly sent you Ksh 3,000 on M-PESA. Kindly return it to this number, I am in an emergency."],
    [S, "CONGRATULATIONS! You have won Ksh 100,000 in the Safaricom Mega Promo. Send your M-PESA PIN to claim your prize."],
    [S, "KRA: You have a tax refund of Ksh 12,400. Claim now at kra-refund-claim.co.ke before it expires today."],
    [S, "Your M-PESA account has been suspended. Click http://mpesa-verify.top to confirm your PIN within 24 hours."],
    [S, "Dear customer, we detected unusual activity. Reply with your M-PESA PIN and ID number to restore your account."],
    [S, "HELB: Your loan of Ksh 45,000 is approved. Pay a processing fee of Ksh 1,500 to 0733111222 to receive funds."],
    [S, "Get an instant loan of Ksh 50,000 without CRB check. Send Ksh 800 insurance fee first to 0722333444."],
    [S, "We are hiring! Work from home and earn Ksh 5,000 per day. Pay registration fee Ksh 1,000 to apply."],
    [S, "Safaricom: you have won a free Toyota. Call 0700123456 now and send Ksh 500 to confirm delivery."],
    [S, "Invest Ksh 5,000 and receive Ksh 20,000 in 24 hours guaranteed. Send to 0745999888 now."],
    [S, "Your SIM will be blocked in 2 hours. Share the OTP sent to your phone to verify your identity."],
    [S, "Hi, this is Safaricom customer care. Your account has a problem, please give me the code you just received."],
    [S, "Landlord notice: change of rent payment number. Send this month's rent to the new number 0711555666 immediately."],
    [S, "URGENT: Your KRA PIN will be deactivated. Update your details at kra-update.info today."],
    [S, "You have been selected for the Ksh 250,000 Youth Fund grant. Send Ksh 300 processing fee to receive your money."],
    [S, "M-PESA reversal: Please send back Ksh 2,000 sent to your number by mistake. Reply fast, I am stranded."],
    [S, "Your ATM card is blocked. Click bit.ly/3xK9 to unlock and enter your PIN."],
    [S, "Dear customer your Fuliza limit has been increased. Pay Ksh 500 activation fee to 0722000111 to enjoy."],
    [S, "Congrats, your number won in the bonus draw. Reply with your ID and PIN to collect Ksh 75,000."],
    [S, "Dear Customer, your Safaricom line will be disconnected. Update your KYC now by sending your ID and PIN."],
    [S, "Ksh 15,000 has been sent to your M-PESA by mistake. Return the funds to avoid legal action, act now."],
    [S, "Final warning: your parcel is held at customs. Pay Ksh 350 clearance fee at link.parcel-ke.xyz to release it."],
    [S, "Earn Ksh 10,000 daily with crypto trading. Deposit Ksh 2,000 now and withdraw double in one hour."],
    [S, "Hi, it's Brian. I lost my phone, this is my new number. Please send Ksh 3,500 urgently, I will pay you back tonight."],
    [S, "Hello this is your son, I changed my number. Send me Ksh 5,000 quickly for school fees."],
    [S, "Mum nimepoteza simu, hii ni namba yangu mpya. Tuma pesa ya dharura Ksh 2,000 kwa namba hii."],
    /* ---- scam: Swahili ---- */
    [S, "Nimetuma pesa kwa namba yako kimakosa, tafadhali rudisha Ksh 5,000 haraka, nina dharura."],
    [S, "Hongera! Umeshinda Ksh 200,000 kwenye promosheni ya Safaricom. Tuma namba yako ya siri ili kupokea zawadi."],
    [S, "Akaunti yako ya M-PESA imefungwa. Bonyeza kiungo hiki ili kuthibitisha PIN yako leo."],
    [S, "Pata mkopo wa Ksh 30,000 bila CRB. Lipa ada ya Ksh 700 kwanza kwa namba 0733222111."],
    [S, "Kazi inapatikana, lipa ada ya usajili Ksh 1,000 kisha uanze kupata Ksh 4,000 kila siku."],
    [S, "Samahani nilituma pesa kwa namba isiyo sahihi. Tafadhali nirudishie Ksh 1,800 sasa hivi."],
    [S, "KRA: Una marejesho ya kodi ya Ksh 9,000. Fungua kiungo hiki kabla muda haujaisha leo."],
    [S, "Safaricom: tunahitaji uthibitishe akaunti yako. Tuma nambari ya siri na kitambulisho chako sasa."],
    /* ---- scam: Sheng ---- */
    [S, "Bro nimekutumia pesa kwa bahati mbaya, rudisha 2k fiti, niko kwa shida."],
    [S, "Mambo? Umeshinda 150k kwa promo ya Safaricom, tuma PIN yako upate pesa yako leo."],
    [S, "Mkopo wa haraka 20k bila CRB, tuma 500 ya ada kwanza kisha upokee pesa mara moja."],
    [S, "Umechaguliwa kwa job ya remote, lipa 1k ya form uanze kupata 3k kila siku."],
    [S, "Sasa boss tuma hiyo pesa ya rent kwa namba mpya 0711777888 haraka ata leo."],
    [S, "Account yako ya mpesa imeblock, click link hii ujaze PIN yako uirudishe."],
    [S, "Boss nimetuma 4k kimakosa, tafadhali rudisha, naomba usinipotezee muda."],
    [S, "Safaricom promo: umeshinda iPhone, tuma 600 ya delivery kwa 0745123123 ulete."],
    /* ---- normal: English ---- */
    [L, "TJK4M9X2QA Confirmed. Ksh 350.00 paid to Naivas Westlands on 26/10/26 at 4:48 PM. New M-PESA balance is Ksh 2,140.50."],
    [L, "Confirmed. You have received Ksh 1,500.00 from JANE WANJIRU 0722000123 on 26/10/26. New M-PESA balance is Ksh 3,640.50."],
    [L, "TJK9L2QWE1 Confirmed. Ksh 200.00 sent to Mama Mboga 0733000321 on 26/10/26. Transaction cost Ksh 7.00."],
    [L, "You have bought 1GB data bundle valid for 24 hours. Dial *544# to check your balance."],
    [L, "Your Fuliza limit is Ksh 5,000. Outstanding Fuliza amount is Ksh 0. Repay on time to keep your limit."],
    [L, "Thank you for paying your KPLC token. Your units are 12.4. Token: 1234 5678 9012 3456."],
    [L, "Hi, are we still meeting at 6pm at the mall today?"],
    [L, "Your parcel from Jumia has been dispatched and will arrive tomorrow between 10am and 2pm."],
    [L, "Equity Bank: Ksh 5,000 has been debited from your account ending 4521 at Quickmart. Available balance Ksh 12,300."],
    [L, "Reminder: your appointment at the clinic is on Friday at 9am. Reply YES to confirm."],
    [L, "Safaricom: Your monthly bundle has been renewed. Dial *544# to check your balance. Never share your PIN with anyone."],
    [L, "Mum, I reached home safely. Will call you later tonight."],
    [L, "The meeting has been moved to Thursday at 10am in the boardroom. Please confirm attendance."],
    [L, "Your Uber trip is complete. Total fare Ksh 480. Thank you for riding."],
    [L, "School fees payment of Ksh 15,000 received for Mary Njeri. Thank you."],
    [L, "Your M-PESA statement for October is attached. The password is sent separately. Never share your PIN with anyone."],
    [L, "Thanks for banking with us. Your loan repayment of Ksh 3,000 has been received. Balance Ksh 27,000."],
    [L, "Happy birthday! Hope you have a wonderful day, let's catch up this weekend."],
    [L, "You have received 20 free minutes valid until midnight. Enjoy."],
    [L, "TKM2P7ZQ88 Confirmed. Ksh 1,000.00 withdrawn from agent 345678 Westlands on 26/10/26."],
    [L, "Your electricity bill for October is Ksh 2,450 due on 5 November. Pay via M-PESA paybill 888880."],
    [L, "Dear customer, your SIM swap request was successful. If you did not request this, call 100 immediately."],
    [L, "Your order has been delivered. Rate your experience and get a discount on your next order."],
    [L, "Salary for October has been credited to your account. Net pay Ksh 55,200. Statement available in the app."],
    [L, "Hi, it's Brian. I will be late, the meeting starts at 10."],
    [L, "Hi it's Brian, are we still on for lunch tomorrow?"],
    [L, "Mum, hii ni nambari yangu ya kazi, nitaipiga baadaye."],
    /* ---- normal: Swahili ---- */
    [L, "Habari, nimefika. Niko nje ya geti, nifungulie tafadhali."],
    [L, "Kikao kimeahirishwa hadi kesho saa nne asubuhi. Tafadhali thibitisha kuhudhuria."],
    [L, "Asante kwa kununua bundles. Una MB 1000 zinazotumika hadi saa sita usiku."],
    [L, "Mama anaomba umpigie simu ukipata nafasi. Hakuna dharura, anataka kusalimia tu."],
    [L, "Karibu Safaricom. Salio lako la M-PESA ni Ksh 2,300. Usishiriki PIN yako na yeyote."],
    [L, "Tutaonana kesho kanisani saa tatu asubuhi. Mungu akubariki."],
    [L, "Malipo yako ya umeme yamepokelewa. Asante kwa kutumia KPLC."],
    [L, "Nimekutumia ile pesa ya nauli kupitia M-PESA, angalia simu yako."],
    /* ---- normal: Sheng ---- */
    [L, "Niko njiani bro, nitafika baada ya dakika 20. Tuko poa."],
    [L, "Umefika nyumbani? Tuma text ukifika, usisahau kuniambia kama kuko poa."],
    [L, "Msee tutakutana wapi leo, ni saa ngapi tuanze mechi?"],
    [L, "Nimekutumia 500 ya lunch, enda ule vizuri. Tuko pamoja."],
    [L, "Mambo bro, game ya leo ni saa kumi na mbili, usichelewe."],
    [L, "Rent ya mwezi huu nimelipa kwa landlord kama kawaida, receipt ninaiweka kwa group."],
    [L, "Nimepata kazi mpya! Tutaongea jioni nikifika nyumbani nikueleze yote."],
    [L, "Safaricom: Umenunua dakika 50 za kupiga simu zinazotumika kwa siku 7. Asante."],
    /* ---- scam: phishing, fake jobs, investment, parcels ---- */
    [S, "Your M-PESA account has been suspended due to unusual activity. Click http://mpesa-verify.top to restore access within 24 hours."],
    [S, "Dear customer your KCB account will be closed today. Update your details now at kcb-secure-login.xyz"],
    [S, "Akaunti yako ya M-PESA imesimamishwa. Bonyeza kiungo hiki kuthibitisha: bit.ly/3xYz12"],
    [S, "Your SIM will be deactivated in 2 hours. Call 0733 112 233 now to re-register and avoid disconnection."],
    [S, "KYC update required. Your line will be blocked. Verify now at safaricom-kyc.info"],
    [S, "We are hiring! Earn Ksh 3,000 daily working from home. Pay Ksh 500 registration and send your ID number on WhatsApp 0711 223 344."],
    [S, "Nafasi za kazi Safaricom. Lipa ada ya usajili Ksh 1,000 kisha tuma CV yako WhatsApp."],
    [S, "Invest Ksh 5,000 and get Ksh 20,000 in 7 days. Guaranteed returns, join our forex group now."],
    [S, "Double your money in 24 hours with our crypto trading platform. Deposit now, withdraw daily profits."],
    [S, "Uwekezaji wa uhakika! Weka Ksh 2,000 upate Ksh 10,000 ndani ya siku 3. Jiunge sasa."],
    [S, "Your parcel is on hold at customs. Pay Ksh 350 clearance fee at https://dhl-redeliver.click to release it."],
    [S, "Courier: delivery failed. Confirm your address and pay the redelivery fee to receive your package."],
    [S, "I am stranded in hospital after an accident, please send Ksh 4,000 urgently, I will explain later."],
    [S, "Dear customer, a refund of Ksh 8,200 is waiting. Call our agent on 0722 998 877 immediately to claim."],
    [S, "Dear customer your Family Bank account will be closed today. Confirm your PIN and ID at familybank-verify.top to keep it active."],
    [S, "Ujumbe kutoka Safaricom: umeshinda gari! Tuma Ksh 2,500 ada ya usajili kwa 0715 000 111 upate zawadi."],
    [S, "Hi dear, my phone got stolen so I am using a friend's number. Please send Ksh 3,000 to this number, I will pay you back tonight."],
    [S, "Sorry I deposited Ksh 6,000 into your M-PESA by mistake. Please send it back to 0722 000 333, I am begging you."],
    [S, "Final warning: your tax account is flagged. Pay Ksh 9,500 to avoid legal action at kra-clear.click now."],
    [S, "Get Ksh 20,000 loan instantly, no security. Pay Ksh 1,000 verification first to 0700 454 545."],
    [S, "Your ATM card is blocked. Call 0733 800 800 now and give your PIN to unblock it."],
    [S, "Work from home! Earn Ksh 5,000 per day. Pay Ksh 600 to join the WhatsApp group today. Limited slots."],
    [S, "Hello, I am a lawyer. A relative of yours left you Ksh 12 million. Send Ksh 4,000 to start the paperwork."],
    [S, "Safaricom promo: reply with your M-PESA PIN to receive your Ksh 15,000 Mwaka Mpya bonus."],
    [S, "Parcel from Amazon is held. Pay Ksh 350 customs at amazon-ke-parcel.xyz to receive it."],
    [S, "Umechaguliwa kupata mkopo wa Ksh 30,000. Tuma Ksh 800 bima kwanza kisha pesa zitaingia."],
    [S, "Acct alert: Unusual activity detected. Verify at mpesa-safe-check.online or your line will be barred."],
    [S, "Boss I am in hospital and need Ksh 8,000 urgently, my M-PESA is not working. Send to 0746 100 200 please."],
    /* ---- normal: look-alikes that are fine ---- */
    [L, "Your Jumia order 458221 has shipped and arrives Thursday. Track it at jumia.co.ke/track"],
    [L, "KCB: Your OTP is 482913. Do not share it with anyone. KCB will never ask for it."],
    [L, "Hi, I applied for the job at the bank. Interview is on Monday at 10am, wish me luck!"],
    [L, "Your package from Amazon was delivered to the front desk today at 2:15 PM."],
    [L, "Reminder: your account statement for October is ready. Log in to the official app to view it."],
    [L, "Boss, the forex rates today are good, I will convert the dollars after lunch."],
    [L, "Equity: Ksh 3,200.00 debited from your account ending 9087 on 01-Nov. Available balance Ksh 14,500.00. Call 0763 000 000 if this was not you."],
    [L, "Co-op Bank: Ksh 8,000 credited to account 0112233445. Balance Ksh 21,300. For any query call 0711 049 000."],
    [L, "Stanbic: your card ending 5521 was used for Ksh 2,400 at Quickmart. If you did not make this payment call 0711 068 000."],
    [L, "DTB: Ksh 15,000 transferred to account ending 3340 successfully. Thank you for banking with us."],
    [L, "Your DHL shipment 4412398 is out for delivery today. The rider will call you on arrival. Track at dhl.com/ke"],
    [L, "Posta: your parcel has arrived at the Nairobi GPO. Collect it with your ID within 14 days."],
    [L, "Glovo: your order is on the way. Rider Kevin will arrive in 12 minutes."],
    [L, "M-PESA: You have paid Ksh 450.00 to KPLC PREPAID. Token 4411 8822 3344 5566. Units 18.3."],
    [L, "Confirmed. Ksh 700.00 sent to Airtime on 2/11/26. M-PESA balance is Ksh 2,115.00. Transaction cost Ksh 0.00."],
    [L, "Safaricom: Your Tunukiwa offer is ready. Buy 2GB for Ksh 50 by dialling *444#. Offer valid till midnight."],
    [L, "Nimepokea pesa, asante sana. Nitakupigia kesho asubuhi tuongee kuhusu shamba."],
    [L, "Mama anauliza kama utakuja Jumapili. Amepika chapati na nyama."],
    [L, "Poa bro, tuma ile document kwa email nitaiona jioni. Asante."],
    [L, "Reminder: school fees balance is Ksh 12,000 due 15 November. Pay via the school paybill 400200 and keep the receipt."],
    [L, "Your clinic appointment is on Tuesday at 9am. Please arrive 15 minutes early and carry your insurance card."],
    [L, "Hi team, standup is moved to 9:30 tomorrow. Please update your tickets before then."],
    [L, "Your Netflix payment of Ksh 1,100 was successful. Enjoy watching! Manage your plan at netflix.com/account"],
    [L, "Bolt: your trip receipt for Ksh 640 is ready. Thanks for riding with us."],
    [L, "Happy new month! Wishing you a productive one. Let's catch up this weekend."],
    [L, "KRA: Your PIN certificate has been generated. Download it from itax.kra.go.ke using your own login."],
    [L, "Hi Mr Mwangi, your loan repayment of Ksh 4,500 is due on the 5th. Pay through the official paybill listed on your agreement."],
    [L, "Imeandikwa: mkutano wa wazazi shuleni ni Ijumaa saa nane mchana. Tafadhali fika kwa wakati."],
    [L, "Sorry for the late reply, I was in a meeting. I will send the report by 5pm."],
    [L, "Absa: A transfer of Ksh 25,000 to your account was received. Reference SALARY NOV. Balance Ksh 31,420."]
  ];

  /* ---------- text to features ---------- */
  function tokens(text) {
    var t = String(text).toLowerCase();
    t = t.replace(/https?:\/\/\S+|www\.\S+|\b[a-z0-9-]+(\.[a-z0-9-]+)*\.(co\.ke|com|net|org|info|xyz|top|link|ke)\b\S*|\bbit\.ly\S*/g, " urlx ");
    t = t.replace(/(\+?254|\b0)[17]\d{2}[\s-]?\d{3}[\s-]?\d{3}\b/g, " phonex ");
    t = t.replace(/\b[a-z]{3}\d[a-z0-9]{5,7}\b/g, " codex ");
    t = t.replace(/\d[\d,]*(\.\d+)?k?/g, " numx ");
    var w = t.split(/[^a-z']+/).filter(function (x) { return x.length > 1 || x === "k"; });
    var out = w.slice(), i;
    for (i = 0; i < w.length - 1; i++) out.push(w[i] + "_" + w[i + 1]);
    return out;
  }

  /* ---------- naive Bayes ---------- */
  function train(rows) {
    var m = { c: [{}, {}], n: [0, 0], docs: [0, 0], vocab: {} }, i, j, f;
    for (i = 0; i < rows.length; i++) {
      var y = rows[i][0], fs = tokens(rows[i][1]);
      m.docs[y]++;
      for (j = 0; j < fs.length; j++) {
        f = fs[j]; m.c[y][f] = (m.c[y][f] || 0) + 1; m.n[y]++; m.vocab[f] = 1;
      }
    }
    m.v = Object.keys(m.vocab).length;
    return m;
  }
  function logp(m, y, f) { return Math.log(((m.c[y][f] || 0) + 1) / (m.n[y] + m.v)); }
  function prob(m, text) {
    /* Start from a cautious prior: most real messages are not scams. Words never seen in training are ignored. */
    var fs = tokens(text), z = -1.6, i, known = 0;
    for (i = 0; i < fs.length; i++) {
      if (!m.vocab[fs[i]]) continue;
      known++; z += logp(m, S, fs[i]) - logp(m, L, fs[i]);
    }
    /* naive Bayes is overconfident, so soften the score before turning it into a probability */
    var k = 1 / Math.max(2.5, Math.sqrt(known));
    return 1 / (1 + Math.exp(-z * k));
  }
  var STOP = { to:1, it:1, by:1, the:1, of:1, you:1, your:1, and:1, is:1, in:1, for:1, on:1, a:1, an:1, be:1, are:1, we:1, i:1, me:1, my:1, ya:1, na:1, kwa:1, wa:1, hii:1, yako:1, number:1, have:1, has:1, with:1, this:1, that:1, will:1, at:1, from:1, so:1, can:1, please:1 };
  function topWords(m, text, n) {
    var fs = tokens(text), seen = {}, arr = [], i, f, d;
    for (i = 0; i < fs.length; i++) {
      f = fs[i];
      if (seen[f] || STOP[f] || f.indexOf("_") > -1 || /^(url|phone|code|num)x$/.test(f)) continue;
      seen[f] = 1; d = logp(m, S, f) - logp(m, L, f);
      if (d > 0.9 && (m.c[S][f] || 0) >= 2) arr.push([f, d]);
    }
    arr.sort(function (a, b) { return b[1] - a[1]; });
    return arr.slice(0, n || 4).map(function (a) { return a[0]; });
  }
  function crossVal(rows, k) {
    var ok = 0, i, tr, te;
    for (var fold = 0; fold < k; fold++) {
      tr = []; te = [];
      for (i = 0; i < rows.length; i++) (i % k === fold ? te : tr).push(rows[i]);
      var m = train(tr);
      for (i = 0; i < te.length; i++) if ((prob(m, te[i][1]) >= 0.5 ? 1 : 0) === te[i][0]) ok++;
    }
    return ok / rows.length;
  }

  var MODEL = train(DATA);
  var STATS = (function () {
    var s = 0, l = 0, i;
    for (i = 0; i < DATA.length; i++) DATA[i][0] ? s++ : l++;
    return { total: DATA.length, scam: s, ok: l, features: MODEL.v, cv: crossVal(DATA, 5) };
  })();

  /* ---------- plain-language rules (explain the verdict) ---------- */
  /* Link check: where does a link really go? Official sites are fine. Short links, look-alikes of real brands,
     bare IP addresses and throw-away domain endings are flagged. */
  var OFFICIAL = /(^|\.)(safaricom\.(co\.ke|com|net)|m-pesa\.com|mpesa\.(co\.ke|com)|[a-z0-9-]+\.go\.ke|equitybank\.co\.ke|equitygroupholdings\.com|kcbgroup\.com|co-opbank\.co\.ke|absa\.co\.ke|stanbicbank\.co\.ke|ncbagroup\.com|dtbafrica\.com|airtelkenya\.com|airtel\.co\.ke|telkom\.co\.ke|jumia\.co\.ke|kplc\.co\.ke|google\.com|dhl\.(com|co\.ke)|fedex\.com|ups\.com|posta\.co\.ke|sendyit\.com|youtube\.com|facebook\.com|whatsapp\.com|wa\.me|instagram\.com|twitter\.com|x\.com|linkedin\.com|apple\.com|microsoft\.com|amazon\.com|netflix\.com|uber\.com|bolt\.eu)$/;
  var SHORT = /^(bit\.ly|tinyurl\.com|cutt\.ly|t\.ly|rb\.gy|is\.gd|goo\.gl|ow\.ly|shorturl\.at|tiny\.cc|buff\.ly|lnkd\.in)$/;
  var BRAND = /(safaricom|mpesa|m-pesa|\bkra\b|ecitizen|equity|kcb|airtel|ntsa|nhif|\bsha\b|huduma|hustler|fuliza|okoa|bonga|co-?op|absa|stanbic|\bdtb\b|ncba|nssf|kplc|ardhi)/;
  var BADTLD = /\.(xyz|top|click|online|site|icu|cfd|shop|live|buzz|link|club|vip|work|cc|tk|ml|ga|cf|gq|info|support|help|cash|loans?|today|store|website|ws|page|monster|rest|fit)$/;
  var KNOWNTLD = /\.(com|net|org|info|biz|co\.ke|go\.ke|or\.ke|ac\.ke|ke|top|xyz|click|online|site|icu|cfd|shop|live|buzz|link|club|vip|work|cc|tk|ml|ga|cf|gq|ly|me|io|co|app|page|support|help|cash|loans?|today|store|tech|website|ws|to|gl|gd|monster|rest|fit|at)$/;
  function urlRisk(text) {
    var rx = /(?:https?:\/\/)?(?:www\.)?([a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)+)(?::\d+)?(?:\/[^\s]*)?/gi, m, d, found = null, hasScheme;
    text = String(text);
    while ((m = rx.exec(text))) {
      d = m[1].toLowerCase(); hasScheme = /^(https?:\/\/|www\.)/i.test(m[0]);
      if (/^\d{1,3}(\.\d{1,3}){3}$/.test(d)) { if (hasScheme || /\//.test(m[0])) return { kind: "linkbad", domain: d }; continue; }
      if (!hasScheme && !KNOWNTLD.test(d)) continue;
      if (!hasScheme && /^\d/.test(d)) continue;
      if (OFFICIAL.test(d)) continue;
      if (SHORT.test(d)) return { kind: "linkshort", domain: d };
      if (BRAND.test(d)) return { kind: "linkbrand", domain: d };
      if (BADTLD.test(d)) return { kind: "linkbad", domain: d };
      if (!found) found = { kind: "linkunknown", domain: d };
    }
    return found;
  }
  var RULES = [
    ["pin", function (t) {
      return /\b(pin|otp|password|nambari ya siri|namba ya siri|code|msimbo)\b/.test(t) &&
        /\b(send|share|give|reply|enter|tuma|toa|weka|ujaze|thibitisha|confirm|click|bonyeza)\b/.test(t) &&
        !/(never share|do not share|don't share|usishiriki|usimpe)/.test(t);
    }],
    ["reversal", function (t) {
      return /(revers|wrong number|by mistake|mistakenly|kimakosa|kwa bahati mbaya|rudisha|nirudishie|send back|return (it|the)|isiyo sahihi)/.test(t);
    }],
    ["fee", function (t) {
      return /(fee|ada ya|ada\b|processing|registration|usajili|insurance|clearance|activation|delivery)/.test(t) &&
        /\b(pay|send|lipa|tuma|deposit)\b/.test(t);
    }],
    ["prize", function (t) { return /(you have won|you won|congrat|hongera|umeshinda|won in|mega promo|bonus draw|selected for)/.test(t); }],
    ["link", function (t) {
      if (/(https?:\/\/|www\.|bit\.ly|\.top\b|\.xyz\b|\.info\b|[a-z]-[a-z-]+\.co\.ke)/.test(t)) return !!urlRisk(t);
      return /(kiungo|link hii|click)/.test(t);
    }],
    ["urgent", function (t) { return /(urgent|immediately|expires today|before it expires|within 24 hours|in 2 hours|haraka|sasa hivi|act now|final warning|ata leo|kabla muda)/.test(t); }],
    ["newcontact", function (t) {
      return /(my new number|this is my new number|i changed my number|changed my number|lost my phone|namba yangu mpya|nambari yangu mpya|nimepoteza simu)/.test(t);
    }],
    ["account", function (t) {
      return /(account|akaunti|\bsim\b|your line|laini yako|m-?pesa|kyc|card)/.test(t) &&
        /(suspend|will be (closed|deactivated|blocked|locked|disconnected)|deactivat|disconnect|imesimamishwa|itafungwa|imezuiwa|unusual activity|verify|thibitisha|re-?register|sasisha|update your)/.test(t) &&
        /(click|link|https?:\/\/|www\.|bit\.ly|call|piga|login|log in|bonyeza|within \d+ (hours|minutes)|ndani ya)/.test(t);
    }],
    ["job", function (t) {
      return /(job|vacanc|recruit|hiring|we are hiring|ajira|nafasi za kazi|work from home|part-?time|earn ksh)/.test(t) &&
        /(registration|usajili|fee\b|ada\b|deposit|pay ksh|lipa|whatsapp|telegram|send your (cv|id)|daily)/.test(t) && !/(interview is on|wish me luck)/.test(t);
    }],
    ["invest", function (t) {
      return /(invest|forex|crypto|bitcoin|binary|double your|triple your|guaranteed (returns|profit)|passive income|uwekezaji|mara dufu)/.test(t) &&
        /(guarantee|daily|per day|within \d+|ndani ya|in \d+ (days|hours)|get ksh|upate ksh|withdraw|join (our|now)|jiunge|% (per|a) )/.test(t);
    }],
    ["parcel", function (t) {
      return /(parcel|package|courier|shipment|customs|dhl|fedex|kifurushi)/.test(t) &&
        /(clearance|customs fee|redeliver|re-deliver|reschedule|on hold|held at|delivery failed|undeliver|pay (the )?(ksh|fee)|lipa ada)/.test(t);
    }],
    ["emergency", function (t) {
      return /(accident|hospital|arrested|stranded|emergency|dharura|mgonjwa|ajali|kidnap)/.test(t) &&
        /(send|tuma|pay|lipa).{0,40}(ksh|kes|\d{3,}|\d+k\b)/.test(t);
    }],
    ["callnow", function (t) {
      if (/(if (this was |it was )?not you|if you did ?n[o']?t|for any (query|queries|enquir|question)|for (more )?(help|information)|to report)/.test(t) && !/(pin|password|otp|code)/.test(t)) return false; /* real bank alerts say "call us if this was not you" */
      return /(call|piga|whatsapp|wasiliana|contact)[^.]{0,30}(\+?254|\b0)[17]\d{2}[\s-]?\d{3}[\s-]?\d{3}/.test(t) &&
        /(urgent|immediately|haraka|account|refund|prize|blocked|suspend|customer care|agent|won|claim|loan|verify|deactivat)/.test(t);
    }],
    ["newnumber", function (t) {
      return /(namba mpya ya|nambari mpya ya|change of .*number|new (payment )?number|send .{0,40}to (the )?new number|tuma .{0,40}namba mpya)/.test(t) &&
        !/(this is my new number|my new number|namba yangu mpya|nambari yangu mpya)/.test(t);
    }]
  ];
  var VERIFIED_SENDERS = ["MPESA", "M-PESA", "SAFARICOM", "SAFARICOM1"];
  /* Sender IDs Safaricom itself uses: MPESA, SAFARICOM, SAFARICOM1, StaffUpdate and similar. Spaces, dashes and case are ignored. */
  function isVerifiedSender(x) { return /^(MPESA|SAFARICOM|STAFFUPDATE|STAFFNEWS)/.test(String(x || "").toUpperCase().replace(/[^A-Z0-9]/g, "")); }

  var TEXT = {
    reasons: {
      list:      { en: "{n} is on Safaricom's confirmed fraud list ({c} reports).", sw: "{n} iko kwenye orodha ya Safaricom ya ulaghai uliothibitishwa (ripoti {c})." },
      pending:   { en: "{n} was reported by {c} customers and is under review.", sw: "{n} imeripotiwa na wateja {c} na inakaguliwa." },
      pin:       { en: "It asks for your PIN or a code. Safaricom never does.", sw: "Inaomba PIN au msimbo wako. Safaricom haifanyi hivyo kamwe." },
      reversal:  { en: "It asks you to send money back. This is the reversal scam.", sw: "Inakuomba urudishe pesa. Huu ni ulaghai wa kurudisha pesa." },
      fee:       { en: "It asks for a fee before you get a loan, job or prize.", sw: "Inaomba ada kabla ya kupata mkopo, kazi au zawadi." },
      prize:     { en: "It says you won something you never entered.", sw: "Inasema umeshinda kitu ambacho hukushiriki." },
      link:      { en: "It has a link that is not an official Safaricom or KRA address.", sw: "Ina kiungo ambacho si anwani rasmi ya Safaricom au KRA." },
      urgent:    { en: "It pushes you to act fast so you do not check.", sw: "Inakusukuma kuchukua hatua haraka bila kuthibitisha." },
      newnumber: { en: "It asks you to pay a new number, not the one you know.", sw: "Inakuambia ulipe namba mpya, si ile unayoijua." },
      sender:    { en: "The sender is not a verified Safaricom sender ID.", sw: "Mtumaji si kitambulisho rasmi cha Safaricom." },
      model:     { en: "Linda's model finds the wording close to known scams.", sw: "Modeli ya Linda inaona maneno yanafanana na ulaghai unaojulikana." },
      newcontact:{ en: "It says it is someone you know on a new number. Scammers copy this trick to ask for money.", sw: "Inasema ni mtu unayemjua kwa namba mpya. Matapeli hutumia mbinu hii kuomba pesa." },
      impersonation:{ en: "It says it is from {name}, but {name} is saved with a different number ({n}).", sw: "Inasema ni kutoka kwa {name}, lakini {name} amehifadhiwa kwa namba tofauti ({n})." },
      account:   { en: "It says your account or line will be blocked unless you click or call. Real companies do not threaten you like this.", sw: "Inasema akaunti au laini yako itazuiwa usipobonyeza au kupiga simu. Kampuni halisi hazikutishii hivi." },
      job:       { en: "It offers a job but asks for a fee or your ID on WhatsApp. Real employers never charge you to apply.", sw: "Inatoa kazi lakini inaomba ada au kitambulisho kwa WhatsApp. Waajiri halisi hawakutozi ili uombe kazi." },
      invest:    { en: "It promises fast, guaranteed profit. Real investments never guarantee returns like this.", sw: "Inaahidi faida ya haraka na ya uhakika. Uwekezaji halisi hauahidi faida kama hii." },
      parcel:    { en: "It says a parcel is held and asks you to pay. Couriers do not collect fees through a text link.", sw: "Inasema kifurushi kimezuiliwa na inakuomba ulipe. Kampuni za usafirishaji hazikusanyi ada kwa kiungo cha SMS." },
      emergency: { en: "It describes an emergency and asks for money fast. Call the person first to check.", sw: "Inaeleza dharura na kuomba pesa haraka. Mpigie mtu huyo kwanza ili uthibitishe." },
      callnow:   { en: "It tells you to call a number urgently about money or your account. This is how scammers reach you.", sw: "Inakuambia upige namba haraka kuhusu pesa au akaunti yako. Hivi ndivyo matapeli wanavyokufikia." },
      linkshort: { en: "It has a shortened link ({n}) that hides where it goes.", sw: "Ina kiungo kilichofupishwa ({n}) kinachoficha kinakoelekea." },
      linkbrand: { en: "The link ({n}) copies the name of a real company, but it is not their website.", sw: "Kiungo ({n}) kinaiga jina la kampuni halisi, lakini si tovuti yao." },
      linkbad:   { en: "It has a suspicious link ({n}) that does not look like an official website.", sw: "Ina kiungo cha kutiliwa shaka ({n}) kisichoonekana kama tovuti rasmi." },
      unknownnum:{ en: "No reports about this number yet. Linda cannot confirm who it is.", sw: "Hakuna ripoti kuhusu namba hii bado. Linda haiwezi kuthibitisha ni nani." },
      vernum:    { en: "This number is verified by Safaricom.", sw: "Namba hii imethibitishwa na Safaricom." }
    },
    steps: {
      nosend:  { en: "Do not send money or reply.", sw: "Usitume pesa wala kujibu." },
      nopin:   { en: "Never share your PIN or any code.", sw: "Usishiriki PIN wala msimbo wowote." },
      fwd333:  { en: "Forward it to 333 so Safaricom can act.", sw: "Itume kwa 333 ili Safaricom ichukue hatua." },
      block:   { en: "Do not reply. Block the number in your messages app if you can.", sw: "Usijibu. Zuia namba hiyo kwenye programu yako ya jumbe ukiweza." },
      balance: { en: "Check your real balance with *334#.", sw: "Angalia salio lako halisi kwa *334#." },
      nolink:  { en: "Do not open the link. Use the official app or site.", sw: "Usifungue kiungo. Tumia programu au tovuti rasmi." },
      verify:  { en: "Verify through 100 or *234# before you act.", sw: "Thibitisha kupitia 100 au *234# kabla ya kuchukua hatua." },
      careful: { en: "Still check the name and amount before you pay.", sw: "Bado angalia jina na kiasi kabla ya kulipa." },
      callofficial:{ en: "Contact the company using the number on its official website or your card, not the one in the message.", sw: "Wasiliana na kampuni kwa namba iliyo kwenye tovuti yake rasmi au kadi yako, si ile iliyo kwenye ujumbe." },
      callsaved:{ en: "Call the person on the number you already have saved before you send anything.", sw: "Mpigie mtu huyo kwa namba uliyonayo tayari kabla ya kutuma chochote." }
    }
  };

  /* ---------- scan: combine Safaricom list + rules + model ---------- */
  function normNum(n) { return String(n).replace(/[^0-9]/g, "").replace(/^254/, "0"); }
  function lookupNumber(db, num) {
    var want = normNum(num), k;
    for (k in db) if (normNum(k) === want) return { key: k, rec: db[k] };
    return null;
  }
  /* Contacts are indexed once (number -> contact, first name -> contact) so a scan stays fast even with thousands of contacts. */
  var idxCache = { list: null, idx: null };
  function indexContacts(list) {
    if (idxCache.list === list && idxCache.idx) return idxCache.idx;
    var map = {}, first = {}, i, c, k, fn;
    for (i = 0; i < list.length; i++) {
      c = list[i]; k = normNum(c.n || "");
      if (k && !map[k]) map[k] = c;
      fn = String(c.name || "").toLowerCase().replace(/\(.*?\)/g, " ").trim().split(/\s+/)[0].replace(/[^a-z]/g, "");
      if (fn.length > 1 && !first[fn]) first[fn] = c;
    }
    idxCache = { list: list, idx: { map: map, first: first } };
    return idxCache.idx;
  }
  function scan(text, opts) {
    opts = opts || {}; var db = opts.db || {}, t = String(text).toLowerCase();
    var p = prob(MODEL, text), reasons = [], steps = {}, r, i, hit = null;
    var senderIsVerified = !!(opts.sender && isVerifiedSender(opts.sender));
    /* Messages from Safaricom's own sender IDs are trusted, as before. The only thing that can still raise a flag is a link to a non-official website. */
    if (senderIsVerified) {
      var vl = urlRisk(text);
      if (!(vl && /^(linkshort|linkbrand|linkbad)$/.test(vl.kind))) return { score: 3, level: "ok", reasons: [], words: [], steps: ["careful"] };
    }
    if (opts.sender) hit = lookupNumber(db, opts.sender);
    if (hit && hit.rec.status === "confirmed") { reasons.push({ k: "list", n: hit.key, c: hit.rec.count }); p = Math.max(p, 0.99); }
    else if (hit && hit.rec.status === "pending") { reasons.push({ k: "pending", n: hit.key, c: hit.rec.count }); p = Math.max(p, 0.7); }
    var phones = String(text).match(/(\+?254|\b0)[17]\d{2}[\s-]?\d{3}[\s-]?\d{3}/g) || [];
    for (i = 0; i < phones.length; i++) {
      var h2 = lookupNumber(db, phones[i]);
      if (h2 && h2.rec.status === "confirmed" && !hit) { reasons.push({ k: "list", n: h2.key, c: h2.rec.count }); p = Math.max(p, 0.97); break; }
    }
    var cidx = opts.contactIndex || indexContacts(opts.contacts || []), senderKey = opts.sender ? normNum(opts.sender) : "", senderSaved = !!(senderKey && cidx.map[senderKey]), imp = null;
    var asksMoney = /(send|tuma|lipa|pay|deposit).{0,50}(ksh|kes|\d+\s?k\b|\d{3,})|(ksh|kes)\s?\d/.test(t);
    if (opts.sender && !senderSaved && !senderIsVerified) {
      var introRx = /\b(?:it's|it is|this is|i am|i'm|ni|mimi ni)\s+([a-z]+)\b/g, mi;
      while (!imp && (mi = introRx.exec(t))) if (cidx.first[mi[1]]) imp = cidx.first[mi[1]];
    }
    var fired = 0, strong = false;
    for (i = 0; i < RULES.length; i++) if (RULES[i][1](t)) {
      fired++; reasons.push({ k: RULES[i][0] });
      if (/^(pin|reversal|fee|prize|newnumber|account|job|invest|parcel|emergency|callnow)$/.test(RULES[i][0])) strong = true;
    }
    var lk = urlRisk(text);
    if (lk && lk.kind !== "linkunknown") { fired++; strong = true; reasons.push({ k: lk.kind, n: lk.domain }); }
    else if (lk && !reasons.some(function (x) { return x.k === "link"; })) { reasons.push({ k: "link" }); fired++; }
    if (imp) {
      reasons.unshift({ k: "impersonation", name: imp.name, n: imp.n });
      p = Math.max(p, asksMoney ? 0.88 : 0.55); strong = true;
    } else if (opts.sender && !senderSaved && !senderIsVerified && reasons.some(function (x) { return x.k === "newcontact"; })) {
      p = asksMoney ? Math.max(p, 0.82) : Math.min(Math.max(p, 0.5), 0.62);
    }
    /* Guard for real inboxes: with no rule, no list hit and no money or link in the text, the model alone cannot raise an alarm. */
    if (!reasons.length) {
      var money = /ksh|kes|\bsh\.?\s?\d|m-?pesa|pesa\b|tuma|\d+\s?k\b/.test(t), link = /https?:\/\/|www\.|\b[a-z0-9-]+\.(co\.ke|com|top|xyz|info|net)\b/.test(t);
      if (!(money || link) || senderSaved || p < 0.6) p = Math.min(p, 0.4);
    }
    if (opts.sender && !senderIsVerified && p >= 0.5 && /[a-z]/i.test(opts.sender)) reasons.push({ k: "sender" });
    if (senderSaved && !strong) p = Math.min(p, 0.55);
    if (fired >= 2 && p < 0.75 && !(senderSaved && !strong)) p = Math.max(p, 0.75);
    else if (fired === 1 && p < 0.5 && !senderIsVerified && strong) p = Math.max(p, 0.5);
    if (senderIsVerified && fired === 0) p = Math.min(p, 0.1);
    var level = p >= 0.75 ? "scam" : p >= 0.45 ? "caution" : "ok";
    var words = level === "ok" ? [] : topWords(MODEL, text, 4);
    if (level !== "ok" && !reasons.length) reasons.push({ k: "model" });
    var ks = reasons.map(function (x) { return x.k; });
    if (level !== "ok") {
      if (ks.indexOf("pin") > -1 || ks.indexOf("prize") > -1 || ks.indexOf("fee") > -1) steps.nopin = 1;
      steps.nosend = 1;
      if (ks.indexOf("reversal") > -1) steps.balance = 1;
      if (ks.indexOf("impersonation") > -1 || ks.indexOf("newcontact") > -1) steps.callsaved = 1;
      if (ks.indexOf("link") > -1 || ks.indexOf("linkbad") > -1 || ks.indexOf("linkshort") > -1 || ks.indexOf("linkbrand") > -1) steps.nolink = 1;
      if (/^(account|parcel|job|invest|callnow|linkbrand|linkbad|linkshort)$/.test(ks.join(",")) || ks.some(function (x) { return /^(account|parcel|job|invest|callnow|linkbrand|linkbad|linkshort)$/.test(x); })) steps.callofficial = 1;
      steps.fwd333 = 1; if (opts.sender) steps.block = 1;
      if (level === "caution") steps.verify = 1;
    } else steps.careful = 1;
    return { score: Math.round(p * 100), level: level, reasons: reasons.slice(0, 4), words: words, steps: Object.keys(steps) };
  }
  function fill(tpl, r) { return tpl.split("{n}").join(r.n || "").split("{c}").join(r.c || "").split("{name}").join(r.name || ""); }
  function reasonText(r, lang) { return fill(TEXT.reasons[r.k][lang], r); }
  function stepText(k, lang) { return TEXT.steps[k][lang]; }

  var api = { scan: scan, prob: function (t) { return prob(MODEL, t); }, stats: STATS, data: DATA, TEXT: TEXT,
              reasonText: reasonText, stepText: stepText, lookupNumber: lookupNumber, urlRisk: urlRisk, isVerifiedSender: isVerifiedSender, normNum: normNum, indexContacts: indexContacts, tokens: tokens,
              _train: train, _prob: prob, _cv: crossVal };
  return api;
})();
if (typeof module !== "undefined") module.exports = LindaModel;
