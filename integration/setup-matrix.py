import concurrent.futures, os, hashlib, json, pathlib, subprocess, tarfile, zipfile
ROOT=pathlib.Path(__file__).resolve().parent.parent/'.integration-work/runtime-tools';ROOT.mkdir(parents=True,exist_ok=True)
VERSIONS={'1.8.8':8,'1.12.2':8,'1.13.2':8,'1.16.5':8,'1.17.1':17,'1.18.2':17,'1.20.4':17,'1.20.6':21,'1.21.11':21,'26.3':25}
def download(url,path):
 if path.exists():return
 subprocess.run(['curl','-L','--fail','--retry','2','--max-time','300','-sS',url,'-o',str(path)+'.part'],check=True);pathlib.Path(str(path)+'.part').rename(path)
def java(v):
 if os.environ.get(f'ECLEAN_JAVA_{v}_HOME'):return
 p=ROOT/f'java{v}';p.mkdir(exist_ok=True);archive=ROOT/f'java{v}.tgz';download(f'https://api.adoptium.net/v3/binary/latest/{v}/ga/linux/x64/jdk/hotspot/normal/eclipse',archive)
 if not list(p.glob('*/bin/javac')):
  with tarfile.open(archive) as t:t.extractall(p,filter='data')
 print('JAVA',v,flush=True)
def gradle():
 archive=ROOT/'gradle.zip';download('https://downloads.gradle.org/distributions/gradle-8.10-bin.zip',archive)
 if not (ROOT/'gradle-8.10/bin/gradle').exists():
  with zipfile.ZipFile(archive) as z:z.extractall(ROOT)
 (ROOT/'gradle-8.10/bin/gradle').chmod(0o755)
 print('GRADLE',flush=True)
def paper(v):
 p=ROOT/'servers'/v;p.mkdir(parents=True,exist_ok=True);meta=p/'builds.json';download(f'https://fill.papermc.io/v3/projects/paper/versions/{v}/builds',meta)
 build=json.loads(meta.read_text())[0];(p/'selected.json').write_text(json.dumps(build,indent=2));data=build['downloads']['server:default'];download(data['url'],p/'paper.jar')
 assert hashlib.sha256((p/'paper.jar').read_bytes()).hexdigest()==data['checksums']['sha256'];print('PAPER',v,build['id'],flush=True)
def papi():
 p=ROOT/'papi';p.mkdir(exist_ok=True)
 releases=[('2.11.6','https://repo.extendedclip.com/releases/me/clip/placeholderapi/2.11.6/placeholderapi-2.11.6.jar','b20cb09db1cd79f76b9172e5114dfde6f34c558069e3bd7b72b14f718bc5dffd'),('2.12.3','https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.12.3/PlaceholderAPI-2.12.3.jar','fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437')]
 for version,url,checksum in releases:
  jar=p/f'PlaceholderAPI-{version}.jar';download(url,jar)
  assert hashlib.sha256(jar.read_bytes()).hexdigest()==checksum
 print('PLACEHOLDERAPI 2.11.6 / 2.12.3',flush=True)
if __name__=='__main__':
 with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
  jobs=[pool.submit(java,v) for v in (8,17,21,25)]+[pool.submit(paper,v) for v in VERSIONS]+[pool.submit(gradle),pool.submit(papi)]
  for f in concurrent.futures.as_completed(jobs):f.result()
