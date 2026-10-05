import concurrent.futures,datetime,hashlib,importlib.util,json,os,pathlib,re,shutil,subprocess,sys,zipfile,zlib
ROOT=pathlib.Path(__file__).resolve().parent.parent/".integration-work"
spec=importlib.util.spec_from_file_location('prepare',pathlib.Path(__file__).with_name('prepare-servers.py'));prepare=importlib.util.module_from_spec(spec);spec.loader.exec_module(prepare)
PROJECT=ROOT.parent
RUN=ROOT/'runtime-tools/runs'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S')
TEST_PAPI=os.environ.get('ECLEAN_TEST_PAPI','0')=='1'
def compile_probe():
 out=ROOT/'runtime-tools/probe-classes';out.mkdir(exist_ok=True)
 javac=prepare.java_home(17)/'bin/javac'
 cp=str(ROOT/'runtime-tools/servers/1.8.8/cache/patched_1.8.8.jar')+':'+str(PROJECT/'build/libs/EClean-1.21.0.jar')
 subprocess.run([str(javac),'--release','8','-cp',cp,'-d',str(out),str(PROJECT/'integration/MatrixProbe.java')],check=True)
 with zipfile.ZipFile(ROOT/'runtime-tools/ECleanMatrixProbe.jar','w') as z:
  for p in out.glob('*.class'):z.write(p,p.name)
  z.write(PROJECT/'integration/plugin.yml','plugin.yml')
def disk_marker(p,phase):
 try:
  f=p/'it_unload/region/r.0.0.mca';raw=f.read_bytes();index=(28+20*32)*4;sector=int.from_bytes(raw[index:index+4],'big')>>8;offset=sector*4096;length=int.from_bytes(raw[offset:offset+4],'big');kind=raw[offset+4];body=raw[offset+5:offset+4+length];nbt=zlib.decompress(body) if kind==2 else body
  data={'sector':sector,'length':length,'compression':kind,'has_diamond_palette':b'minecraft:diamond_block' in nbt,'file_sha256':hashlib.sha256(raw).hexdigest()}
 except Exception as e:data={'diagnostic_error':str(e)}
 (p/f'disk-marker-{phase}.json').write_text(json.dumps(data,indent=2))
def run(v):
 src=ROOT/'runtime-tools/servers'/v;p=RUN/v;p.mkdir(parents=True)
 shutil.copyfile(src/'paper.jar',p/'paper.jar');shutil.copyfile(src/'selected.json',p/'selected.json')
 for name in ('cache','libraries','versions'):
  if (src/name).exists():(p/name).symlink_to(src/name,target_is_directory=True)
 plugins=p/'plugins';plugins.mkdir();ec=plugins/'EClean';ec.mkdir()
 jar=PROJECT/'build/libs/EClean-1.21.0.jar';shutil.copyfile(jar,plugins/'EClean.jar');shutil.copyfile(ROOT/'runtime-tools/ECleanMatrixProbe.jar',plugins/'ECleanMatrixProbe.jar')
 if TEST_PAPI:
  papi_version='2.11.6' if prepare.VERSIONS[v]==8 else '2.12.3'
  papi=ROOT/f'runtime-tools/papi/PlaceholderAPI-{papi_version}.jar'
  shutil.copyfile(papi,plugins/papi.name)
  pd=plugins/'PlaceholderAPI';pd.mkdir()
  (pd/'config.yml').write_text('check_updates: false\ncloud_enabled: false\ndetect_malicious_expansions: false\n')
  (p/'papi-version.json').write_text(json.dumps({'version':papi_version,'sha256':hashlib.sha256(papi.read_bytes()).hexdigest()}))
 (p/'eclean-sha256.txt').write_text(hashlib.sha256(jar.read_bytes()).hexdigest())
 (ec/'config.yml').write_text('debug: false\nupdate: false\nduration: 9999999\nmessage: {}\nliving: {enable: false}\ndrop: {enable: false}\nchunk: {enable: false}\ntrashcan: {enable: false}\n')
 for folder,content in [('PluginMetrics','opt-out: true\n'),('bStats','enabled: false\n')]:
  d=plugins/folder;d.mkdir();(d/'config.yml').write_text(content)
 (p/'eula.txt').write_text('eula=true\n');port=25600+list(prepare.VERSIONS).index(v)
 (p/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=world\nlevel-type=flat\ngenerate-structures=false\nallow-nether=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-players=2\npause-when-empty-seconds=-1\nsnooper-enabled=false\n')
 (p/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
 for phase in ('first','restart'):
  if phase=='restart' and not (p/'phase1.complete').exists():break
  print('START',v,phase,flush=True)
  with (p/f'{phase}.log').open('w') as log:
   proc=subprocess.Popen(prepare.java_args(v)+(['-Declean.probe.papi=true'] if TEST_PAPI else [])+['-jar','paper.jar']+([] if v in ('1.8.8','1.12.2','1.13.2') else ['--nogui']),cwd=p,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
   try:proc.wait(timeout=280)
   except subprocess.TimeoutExpired:
    print('TIMEOUT',v,phase,flush=True)
    try:proc.communicate(b'stop\n',timeout=30)
    except subprocess.TimeoutExpired:proc.kill();proc.wait()
  disk_marker(p,phase);print('STOP',v,phase,proc.returncode,flush=True)
 rows=[]
 for name in ('results.tsv','restart-results.tsv'):
  if (p/name).exists():rows+=(p/name).read_text().splitlines()
 passed=sum(s.startswith('PASS') for s in rows);failed=sum(s.startswith('FAIL') for s in rows)
 print('RESULT',v,'pass',passed,'fail',failed,'restart',(p/'restart-results.tsv').exists(),flush=True)
 logs='\n'.join(f.read_text(errors='replace') for f in p.glob('*.log'))
 if re.search(r'Could not pass event .+ to (?:EClean|PlaceholderAPI)|Error occurred while (?:enabling|disabling) (?:EClean|PlaceholderAPI)|NoClassDefFoundError|NoSuchMethodError|UnsupportedClassVersionError',logs):raise RuntimeError('Plugin/probe error in '+str(p))
 if failed or len(rows)<39 or not (p/'restart-results.tsv').exists():raise RuntimeError(str(p))
if __name__=='__main__':
 compile_probe();print('RUN',RUN,flush=True);failed=[]
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  jobs={pool.submit(run,v):v for v in (sys.argv[1:] or list(prepare.VERSIONS))}
  for f in concurrent.futures.as_completed(jobs):
   try:f.result()
   except Exception as e:failed.append(jobs[f]);print('FAILED',jobs[f],str(e),flush=True)
 if failed:raise SystemExit(1)
