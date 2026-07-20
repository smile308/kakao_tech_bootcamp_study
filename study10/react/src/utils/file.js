export function fileToDataUrl(file){
    return new Promise((resolve, reject)=>{
        if(!file){
            reject(new Error("파일이 없습니다."));
            return;
        }
        const reader=new FileReader();
        reader.onload=()=>{
            resolve(reader.result);
        };
        reader.onerror=()=>{
            reject(reader.error);
        };
        reader.readAsDataURL(file);
    });
}