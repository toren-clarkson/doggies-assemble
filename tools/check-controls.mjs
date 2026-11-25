import path from 'node:path';
import {root,serverRoot,compile,files,run,artifacts} from './build.mjs';
const out=path.join(root,'.cache/controls');
compile([path.join(root,'tests/ControlChecks.java')],out,[artifacts.plugin]);
run('java',['-classpath',[out,artifacts.plugin,...files(path.join(serverRoot,'libraries'),'.jar'),...files(path.join(serverRoot,'versions'),'.jar')].join(path.delimiter),'local.dogwand.ControlChecks']);
