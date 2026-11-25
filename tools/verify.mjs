import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {root,base,serverRoot,compile,zip} from './build.mjs';

const work=process.env.DOGWAND_TEST_HOME || path.join(root,'.cache/server-1.3.1');fs.mkdirSync(path.join(work,'plugins'),{recursive:true});
for(const dir of ['libraries','versions','cache']) {
  const destination=path.join(work,dir);
  if(!fs.existsSync(destination)) fs.cpSync(path.join(serverRoot,dir),destination,{recursive:true});
}
compile([path.join(root,'tests/DogWandFixture.java')],path.join(root,'.cache/fixture-classes'),[path.join(root,'dist/dog-wand-paper-26.3.jar')]);
fs.writeFileSync(path.join(root,'.cache/fixture-classes/plugin.yml'),"name: DogWandFixture\nversion: '1'\nmain: DogWandFixture\napi-version: '26.3'\ndepend: [DogWand]\ncommands:\n  dwtest:\n    permission: dwfixture.admin\npermissions:\n  dwfixture.admin:\n    default: op\n");
zip(path.join(root,'.cache/fixture-classes'),path.join(work,'plugins/DogWandFixture.jar'));
fs.copyFileSync(path.join(root,'dist/dog-wand-paper-26.3.jar'),path.join(work,'plugins/DogWand.jar'));
fs.mkdirSync(path.join(work,'world/datapacks'),{recursive:true});
fs.copyFileSync(path.join(root,'dist/dog-wand-datapack-26.3.zip'),path.join(work,'world/datapacks/dog-wand.zip'));
fs.writeFileSync(path.join(work,'eula.txt'),'eula=true\n');
fs.writeFileSync(path.join(work,'server.properties'),'server-ip=127.0.0.1\nserver-port=25625\nonline-mode=false\nwhite-list=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\nmax-tick-time=120000\npause-when-empty-seconds=-1\nallow-nether=false\n');
fs.writeFileSync(path.join(work,'bukkit.yml'),'settings:\n  allow-end: false\n');
let results=[];
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
for(const phase of [1,2]) {
  const log=fs.createWriteStream(path.join(root,`.cache/server-${phase}.log`));
  let text='',waiters=[];
  const proc=spawn('java',['-Xms512M','-Xmx2G','-jar',path.join(base,'corrupted-overworld/.cache/paper.jar'),'nogui'],{cwd:work,windowsHide:true,stdio:['pipe','pipe','pipe']});
  function data(chunk) {
    const s=chunk.toString();text+=s;log.write(s);
    for(const line of s.split('\n')) if(/ERROR|WARN|DWTEST|Done \(/.test(line)) console.log(line.trim());
    for(const w of [...waiters]) if(text.includes(w.marker)) {clearTimeout(w.timer);waiters.splice(waiters.indexOf(w),1);w.resolve();}
  }
  proc.stdout.on('data',data);proc.stderr.on('data',data);
  proc.once('exit',code=>{for(const w of waiters){clearTimeout(w.timer);w.reject(new Error('Server exited before '+w.marker+' (code '+code+')'));}waiters=[];});
  function wait(marker,timeout=60000) {
    if(text.includes(marker)) return Promise.resolve();
    return new Promise((resolve,reject)=>{const w={marker,resolve,reject,timer:setTimeout(()=>reject(new Error('Timeout: '+marker)),timeout)};waiters.push(w);});
  }
  async function test(name,delay=350) {
    await sleep(delay);proc.stdin.write('dwtest '+name+'\n');
    await Promise.race([wait('DWTEST PASS '+name),wait('DWTEST FAIL '+name).then(()=>{throw new Error('Failed: '+name);})]);
    results.push({phase,test:name,status:'PASS'});
    fs.writeFileSync(path.join(root,'.cache/results.json'),JSON.stringify(results,null,2));
  }
  try {
    await wait('Done (',180000);
    if(phase===1) {
      await test('setup');await test('recipe');await test('shift');await test('binding');await test('sitting');await test('standing');await test('highlighted');await test('auto_hostile');await test('auto_hostilecheck');await test('auto_allcheck');await test('auto_offcheck');await test('feeding');await test('fed');await test('corner2');await test('guarded');await test('far');await test('sky');await test('boundary');
      await test('order');await test('ordered');await test('arrived',18000);await test('held',4000);await test('returned',4000);
      await test('release');await test('released');await test('attack');await test('attacking');await test('bitten',7000);await test('recall');
      await test('offhand');await test('offhandcheck');await test('sitcancel');await test('sitcancelcheck');await test('occlusion');
      await test('unloadedcancel');await test('bulk');await test('deathcount');await test('persist');
    } else await test('restart');
  } finally {
    if(proc.exitCode===null) {
      proc.stdin.write('stop\n');
      await Promise.race([new Promise(resolve=>proc.once('exit',resolve)),sleep(45000).then(()=>{if(proc.exitCode===null)proc.kill();})]);
    }
    for(const w of waiters)clearTimeout(w.timer);waiters=[];log.end();
  }
}
console.log('All Dog Wand integration checks passed.');
