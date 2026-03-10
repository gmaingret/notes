// client/scripts/generate-icons.mjs
// Run locally: node scripts/generate-icons.mjs
// Do NOT run on server — commit generated PNGs to git instead
import sharp from 'sharp';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const svgPath = resolve(__dirname, '../public/favicon.svg');

await sharp(svgPath).resize(192).png().toFile(resolve(__dirname, '../public/icon-192.png'));
await sharp(svgPath).resize(512).png().toFile(resolve(__dirname, '../public/icon-512.png'));
console.log('Generated: icon-192.png, icon-512.png');
