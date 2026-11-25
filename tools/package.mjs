import fs from 'node:fs';import path from 'node:path';import {root,zip} from './build.mjs';
const out=path.join(root,'.cache/release');fs.mkdirSync(out,{recursive:true});
for(const [file,dir] of [['dog-wand-paper-26.3.jar','plugins'],['dog-wand-datapack-26.3.zip','datapacks'],['dog-wand-resourcepack-26.3.zip','resourcepacks']]){fs.mkdirSync(path.join(out,dir),{recursive:true});fs.copyFileSync(path.join(root,'dist',file),path.join(out,dir,file));}
for(const f of ['README.md','TESTING.md'])fs.copyFileSync(path.join(root,f),path.join(out,f));
for(const d of ['src','tools','tests','datapack','resourcepack'])fs.cpSync(path.join(root,d),path.join(out,'source',d),{recursive:true});
for(const f of ['pom.xml','README.md','TESTING.md','.gitignore'])fs.copyFileSync(path.join(root,f),path.join(out,'source',f));
zip(out,path.join(root,'dist/Dog-Wand-26.3-development.zip'));console.log('Release packaged');
