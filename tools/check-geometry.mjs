import path from 'node:path';
import {root,serverRoot,compile,files,run,artifacts} from './build.mjs';
const out=path.join(root,'.cache/geometry');
compile([path.join(root,'tests/GeometryChecks.java')],out,[artifacts.plugin]);
run('java',['-classpath',[out,artifacts.plugin,...files(path.join(serverRoot,'libraries'),'.jar')].join(path.delimiter),'GeometryChecks']);
