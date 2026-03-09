// .github/scripts/upload-to-supabase.js
// Uploads APK to Supabase Storage using SUPABASE_SERVICE_ROLE_KEY
import { createClient } from 'npm:@supabase/supabase-js@2';

import fs from 'node:fs';
import path from 'node:path';

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const BUCKET = process.env.SUPABASE_BUCKET || 'apk-artifacts';
const APK_PATH = process.env.APK_PATH;

if (!SUPABASE_URL || !SUPABASE_KEY || !APK_PATH) {
  console.error('Missing required env vars. Ensure SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY and APK_PATH are set.');
  process.exit(1);
}

const supabase = createClient(SUPABASE_URL, SUPABASE_KEY, { auth: { persistSession: false } });

(async () => {
  try {
    // Ensure bucket exists (create if not).
    const { data: buckets, error: listErr } = await supabase.storage.listBuckets();
    if (listErr) {
      console.error('Error listing buckets:', listErr);
    }
    const bucketExists = (buckets || []).some(b => b.name === BUCKET);
    if (!bucketExists) {
      console.log(`Bucket "${BUCKET}" not found — creating it.`);
      const { data: created, error: createErr } = await supabase.storage.createBucket(BUCKET, { public: false });
      if (createErr) {
        console.error('Error creating bucket:', createErr);
        // continue — maybe bucket exists or creation failed due to permissions
      } else {
        console.log('Bucket created:', created);
      }
    }

    const apkFullPath = path.resolve(APK_PATH);
    const fileName = path.basename(apkFullPath);
    const remotePath = `debug/${Date.now()}-${fileName}`;

    const fileBuffer = fs.readFileSync(apkFullPath);

    const { data, error } = await supabase.storage.from(BUCKET).upload(remotePath, fileBuffer, {
      contentType: 'application/vnd.android.package-archive',
      upsert: false,
    });

    if (error) {
      console.error('Upload error:', error);
      process.exit(1);
    }

    // Create a signed URL valid for 7 days (604800 seconds)
    const { data: signed, error: urlErr } = await supabase.storage.from(BUCKET).createSignedUrl(data.path, 60 * 60 * 24 * 7);
    if (urlErr) {
      console.error('Error creating signed URL:', urlErr);
      console.log('Uploaded path:', data.path);
      process.exit(0);
    }
    console.log('Uploaded APK path:', data.path);
    console.log('Signed URL (7d):', signed.signedUrl);
  } catch (err) {
    console.error('Unexpected error:', err);
    process.exit(1);
  }
})();
