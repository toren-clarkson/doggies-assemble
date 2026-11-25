import path from 'node:path';
import {root,serverRoot,compile,files,run,artifacts} from './build.mjs';
const out=path.join(root,'.cache/marker');
compile([path.join(root,'tests/MarkerChecks.java')],out,[artifacts.plugin]);
run('java',['-classpath',[out,artifacts.plugin,...files(path.join(serverRoot,'libraries'),'.jar'),...files(path.join(serverRoot,'versions'),'.jar')].join(path.delimiter),'MarkerChecks']);
