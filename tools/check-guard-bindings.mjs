import path from 'node:path';
import {root,serverRoot,compile,files,run} from './build.mjs';
const out=path.join(root,'.cache/guard-bindings');
compile([path.join(root,'tests/GuardBindingChecks.java')],out,[path.join(root,'dist/dog-wand-paper-26.3.jar')]);
run('java',['-classpath',[out,path.join(root,'dist/dog-wand-paper-26.3.jar'),...files(path.join(serverRoot,'versions'),'.jar'),...files(path.join(serverRoot,'libraries'),'.jar')].join(path.delimiter),'local.dogwand.GuardBindingChecks']);
