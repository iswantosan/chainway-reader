/**
 * Google Apps Script untuk Stok Scanner Android.
 *
 * Cara pakai (pertama kali):
 *   1. Buka spreadsheet target.
 *   2. Pastikan ada 2 tab:
 *        - "Scan"  (header di baris 1: bulan, tanggal, ..., rfid, ..., stok)
 *        - "RFID"  (header di baris 1: rfid, nama barang)
 *   3. Extensions -> Apps Script.
 *   4. Hapus isi Code.gs default, paste isi file ini.
 *   5. Sesuaikan konstanta SCAN_SHEET_NAME / RFID_SHEET_NAME / SHARED_TOKEN.
 *   6. Save (Ctrl+S), beri nama project bebas.
 *   7. Deploy -> New deployment -> Type "Web app".
 *        - Execute as: Me
 *        - Who has access: Anyone
 *      Copy Web app URL ke Settings app.
 *
 * Update kode setelah edit:
 *   - Deploy -> Manage deployments -> pilih deployment -> Edit (icon pensil)
 *     -> Version: New version -> Deploy. URL TETAP SAMA.
 *
 * Payload dari app:
 *   action "ping":
 *     { "token": "...", "action": "ping" }
 *
 *   action "append" (single row):
 *     { "token": "...", "action": "append",
 *       "year": 2026, "month": 5, "date": "09 Mei 2026", "time": "14.07",
 *       "rfid": "EAC...", "qty": 1 }
 *
 *   action "appendBatch" (banyak row sekali kirim):
 *     { "token": "...", "action": "appendBatch",
 *       "items": [
 *         { "year": 2026, "month": 5, "date": "09 Mei 2026", "time": "14.07", "rfid": "EAC1...", "qty": 1 },
 *         ...
 *       ] }
 *
 * Yang ditulis ke sheet "Scan" (insert di baris 2 — data baru selalu di paling atas):
 *   A: tahun (year, angka)
 *   B: bulan (month, angka)
 *   C: tanggal (date, format "09 Mei 2026")
 *   D: jam (time, format "14.07")
 *   E: rfid
 *   F: kosong (nama barang — diisi manual / VLOOKUP)
 *   G: qty (stok)
 *
 * Yang ditulis ke sheet "RFID" (hanya kalau RFID belum terdaftar — insert di baris 2):
 *   A: rfid
 *   (B nama barang dibiarkan, diisi manual oleh user)
 */

const SCAN_SHEET_NAME = 'Scan';
const RFID_SHEET_NAME = 'RFID';
const SHARED_TOKEN = 'GANTI_INI_TOKEN_RAHASIA';

function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents);
    if (!body || body.token !== SHARED_TOKEN) {
      return jsonOut({ ok: false, error: 'unauthorized' });
    }
    const action = body.action || 'append';
    if (action === 'ping') {
      return jsonOut({ ok: true, action: 'ping' });
    }
    if (action !== 'append' && action !== 'appendBatch') {
      return jsonOut({ ok: false, error: 'unknown action: ' + action });
    }

    // Normalisasi: single append diperlakukan sebagai batch berisi 1 item.
    let items;
    if (action === 'append') {
      items = [{
        year: Number(body.year || 0),
        month: Number(body.month || 0),
        date: String(body.date || ''),
        time: String(body.time || ''),
        rfid: String(body.rfid || '').trim(),
        qty: Number(body.qty || 1)
      }];
    } else {
      items = (body.items || []).map(function (it) {
        return {
          year: Number(it.year || 0),
          month: Number(it.month || 0),
          date: String(it.date || ''),
          time: String(it.time || ''),
          rfid: String(it.rfid || '').trim(),
          qty: Number(it.qty || 1)
        };
      });
    }
    if (items.length === 0) {
      return jsonOut({ ok: false, error: 'items kosong' });
    }
    for (let i = 0; i < items.length; i++) {
      if (!items[i].rfid) {
        return jsonOut({ ok: false, error: 'rfid kosong di index ' + i });
      }
    }

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const scanSheet = ss.getSheetByName(SCAN_SHEET_NAME);
    if (!scanSheet) {
      return jsonOut({ ok: false, error: 'sheet "' + SCAN_SHEET_NAME + '" tidak ditemukan' });
    }
    const rfidSheet = ss.getSheetByName(RFID_SHEET_NAME);
    if (!rfidSheet) {
      return jsonOut({ ok: false, error: 'sheet "' + RFID_SHEET_NAME + '" tidak ditemukan' });
    }

    const lock = LockService.getScriptLock();
    lock.waitLock(20000);
    try {
      // 1. Sisipkan N baris di posisi 2 sheet "Scan" sekaligus (efisien & atomik).
      // Layout 7 kolom: A tahun, B bulan, C tanggal, D jam, E rfid, F kosong (nama barang), G stok.
      const N = items.length;
      scanSheet.insertRowsBefore(2, N);
      const rows = items.map(function (it) {
        return [it.year, it.month, it.date, it.time, it.rfid, '', it.qty];
      });
      scanSheet.getRange(2, 1, N, 7).setValues(rows);

      // 2. Daftar RFID unik di batch yang belum ada di sheet "RFID" -> insert sekali di atas.
      const existing = readRfidColumnA(rfidSheet);
      const seenInBatch = {};
      const newRfids = [];
      for (let i = 0; i < items.length; i++) {
        const r = items[i].rfid;
        if (existing[r] || seenInBatch[r]) continue;
        seenInBatch[r] = true;
        newRfids.push(r);
      }
      if (newRfids.length > 0) {
        rfidSheet.insertRowsBefore(2, newRfids.length);
        rfidSheet.getRange(2, 1, newRfids.length, 1)
          .setValues(newRfids.map(function (r) { return [r]; }));
      }

      return jsonOut({
        ok: true,
        inserted: N,
        newRfids: newRfids.length
      });
    } finally {
      lock.releaseLock();
    }
  } catch (err) {
    return jsonOut({ ok: false, error: String(err && err.message || err) });
  }
}

function readRfidColumnA(rfidSheet) {
  const lastRow = rfidSheet.getLastRow();
  const set = {};
  if (lastRow < 2) return set;
  const values = rfidSheet.getRange(2, 1, lastRow - 1, 1).getValues();
  for (let i = 0; i < values.length; i++) {
    const v = String(values[i][0]).trim();
    if (v) set[v] = true;
  }
  return set;
}

function doGet(e) {
  return jsonOut({ ok: true, hint: 'pakai POST untuk append, GET hanya health check' });
}

function jsonOut(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
