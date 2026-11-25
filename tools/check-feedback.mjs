import path from 'node:path';
import {root,serverRoot,compile,files,run,artifacts} from './build.mjs';
const out=path.join(root,'.cache/feedback');
compile([path.join(root,'tests/FeedbackChecks.java')],out,[artifacts.plugin]);
run('java',['-classpath',[out,artifacts.plugin,...files(path.join(serverRoot,'versions'),'.jar'),...files(path.join(serverRoot,'libraries'),'.jar')].join(path.delimiter),'local.dogwand.FeedbackChecks']);
