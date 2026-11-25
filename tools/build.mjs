import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

export const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export const base=path.dirname(root);
// Only this target is implemented. Other versions/backends need compatible code and pack formats.
export const target={minecraft:'26.3',server:'paper',java:25,datapackFormat:[121,0],resourcepackFormat:[97,1]};
export const releaseVersion=fs.readFileSync(path.join(root,'pom.xml'),'utf8').match(/<version>([^<]+)<\/version>/)?.[1];
if(!releaseVersion || !/^[\w.-]+$/.test(releaseVersion))throw new Error('Missing or invalid project version in pom.xml');
const release=`dog-wand-${releaseVersion}`;
export const artifacts={
  plugin:path.join(root,'dist/plugin',target.minecraft,target.server,`${release}-${target.server}-${target.minecraft}.jar`),
  datapack:path.join(root,'dist/datapack',target.minecraft,`${release}-datapack-${target.minecraft}.zip`),
  resourcepack:path.join(root,'dist/resourcepack',target.minecraft,`${release}-resourcepack-${target.minecraft}.zip`),
  bundle:path.join(root,'dist/bundles',target.minecraft,target.server,`Dog-Wand-${releaseVersion}-${target.server}-${target.minecraft}-development.zip`)
};
export const serverRoot=process.env.DOGWAND_PAPER_HOME || path.join(base,'corrupted-overworld/.cache/test-server');
export function files(dir,suffix) {
  return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name),suffix):e.name.endsWith(suffix)?[path.join(dir,e.name)]:[]);
}
export function run(command,args) {
  const result=spawnSync(command,args,{stdio:'inherit',windowsHide:true});
  if(result.error) throw result.error;
  if(result.status!==0) throw new Error(`${command} failed: ${result.status}`);
}
export function compile(sources,out,extra=[]) {
  fs.mkdirSync(out,{recursive:true});
  const libraries=files(path.join(serverRoot,'libraries'),'.jar');
  if(!libraries.some(p=>p.includes('paper-api-26.3'))) throw new Error('Set DOGWAND_PAPER_HOME to an extracted Paper 26.3 server directory.');
  const cp=[...libraries,...files(path.join(serverRoot,'versions'),'.jar'),...extra].join(path.delimiter);
  const args=path.join(root,'.cache',path.basename(out)+'.args');fs.mkdirSync(path.dirname(args),{recursive:true});
  fs.writeFileSync(args,['-encoding','UTF-8','--release',String(target.java),'-classpath',JSON.stringify(cp.replaceAll('\\','/')),'-d',JSON.stringify(out.replaceAll('\\','/')),...sources.map(p=>JSON.stringify(p.replaceAll('\\','/')))].join('\n'));
  run(process.env.DOGWAND_JAVAC || 'javac',['@'+args]);
}
export function zip(source,destination) {
  fs.mkdirSync(path.dirname(destination),{recursive:true});
  run('jar',['--create','--no-manifest','--file',destination,'-C',source,'.']);
}
function json(file,data) {fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');}

export function build() {
  const pluginVersion=fs.readFileSync(path.join(root,'src/main/resources/plugin.yml'),'utf8').match(/^version:\s*(.+)$/m)?.[1]?.trim();
  if(pluginVersion!==releaseVersion)throw new Error('pom.xml and plugin.yml release versions must match');
  const out=path.join(root,'.cache/classes');
  compile(files(path.join(root,'src/main/java'),'.java'),out);
  fs.cpSync(path.join(root,'src/main/resources'),out,{recursive:true});
  zip(out,artifacts.plugin);
  const components={
    'minecraft:max_stack_size':1,
    'minecraft:item_name':{text:'Dog Wand',color:'gold',bold:true},
    'minecraft:item_model':'dogwand:dog_wand',
    'minecraft:custom_data':{PublicBukkitValues:{'dogwand:wand':1}},
    'minecraft:consumable':{consume_seconds:3600,animation:'none',has_consume_particles:false,sound:'minecraft:intentionally_empty'},
    'minecraft:lore':[
      {text:'LEFT Use mode   SHIFT Cycle',color:'yellow',italic:false},
      {text:'RIGHT Bind / unbind your dog',color:'yellow',italic:false},
      {text:'SHIFT + RIGHT Bind all your dogs',color:'yellow',italic:false},
      {text:''},
      ...['Point Order','Sit / Stand','Highlight Dogs','Auto Target','Feed Dogs','Guard Region','Release Orders'].map((name,i)=>({text:(i===0?'> ':'  ')+name,color:i===0?'aqua':'dark_gray',bold:i===0,italic:false})),
      {text:''},
      {text:'Point at a block to stay, or a mob',color:'gray',italic:false},
      {text:'to attack.',color:'gray',italic:false},
      {text:'0 bound dog(s)',color:'dark_aqua',italic:false}
    ]
  };
  const dp=path.join(root,'datapack');
  json(path.join(dp,'pack.mcmeta'),{pack:{min_format:target.datapackFormat,max_format:target.datapackFormat,description:'Dog Wand crafting recipe · requires DogWand Paper plugin'}});
  json(path.join(dp,'data/dogwand/recipe/dog_wand.json'),{type:'minecraft:crafting_shaped',category:'equipment',pattern:['BBB','BSB','BBB'],key:{B:'minecraft:bone',S:'minecraft:stick'},result:{id:'minecraft:stick',count:1,components}});
  json(path.join(dp,'data/dogwand/advancement/recipe.json'),{criteria:{has_bone:{trigger:'minecraft:inventory_changed',conditions:{items:[{items:'minecraft:bone'}]}}},rewards:{recipes:['dogwand:dog_wand']}});
  zip(dp,artifacts.datapack);
  const rp=path.join(root,'resourcepack');
  json(path.join(rp,'pack.mcmeta'),{pack:{min_format:target.resourcepackFormat,max_format:target.resourcepackFormat,description:'Dog Wand · bone-tipped wooden wand'}});
  json(path.join(rp,'assets/dogwand/items/dog_wand.json'),{model:{type:'minecraft:model',model:'dogwand:item/dog_wand'}});
  json(path.join(rp,'assets/dogwand/models/item/dog_wand.json'),{parent:'minecraft:item/handheld',textures:{layer0:'dogwand:item/dog_wand'}});
  if(fs.existsSync(path.join(rp,'assets/dogwand/textures/item/dog_wand.png'))) zip(rp,artifacts.resourcepack);
  console.log('BUILD OK:',artifacts.plugin,artifacts.datapack,artifacts.resourcepack);
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) build();
