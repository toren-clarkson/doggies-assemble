import fs from 'node:fs';import path from 'node:path';import {root,zip,artifacts} from './build.mjs';
// Fresh staging prevents previous targets/releases leaking into a bundle.
const out=fs.mkdtempSync(path.join(root,'.cache/release-'));
for(const [file,dir] of [[artifacts.plugin,'plugins'],[artifacts.datapack,'datapacks'],[artifacts.resourcepack,'resourcepacks']]){fs.mkdirSync(path.join(out,dir),{recursive:true});fs.copyFileSync(file,path.join(out,dir,path.basename(file)));}
for(const f of ['README.md','TESTING.md'])fs.copyFileSync(path.join(root,f),path.join(out,f));
for(const d of ['src','tools','tests','datapack','resourcepack'])fs.cpSync(path.join(root,d),path.join(out,'source',d),{recursive:true});
for(const f of ['pom.xml','README.md','TESTING.md','.gitignore'])fs.copyFileSync(path.join(root,f),path.join(out,'source',f));
fs.mkdirSync(path.join(out,'source/dist'),{recursive:true});fs.copyFileSync(path.join(root,'dist/README.md'),path.join(out,'source/dist/README.md'));
zip(out,artifacts.bundle);console.log('Release packaged:',artifacts.bundle);
