use std::path::PathBuf;

fn main() {
    println!("cargo:rerun-if-changed=src/ffi.rs");
    println!("cargo:rerun-if-changed=src/lib.rs");
    println!("cargo:rerun-if-changed=cbindgen.toml");

    let crate_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let out = crate_dir.join("include").join("cedar_cabi.h");
    std::fs::create_dir_all(out.parent().unwrap()).expect("create include/ dir");

    let cfg = cbindgen::Config::from_file(crate_dir.join("cbindgen.toml"))
        .expect("read cbindgen.toml");
    match cbindgen::Builder::new()
        .with_crate(&crate_dir)
        .with_config(cfg)
        .generate()
    {
        Ok(b) => {
            b.write_to_file(&out);
        }
        Err(e) => {
            eprintln!("cbindgen generate warning: {e}");
            std::fs::write(
                &out,
                "#ifndef CEDAR_CABI_H\n#define CEDAR_CABI_H\n\
                 /* bootstrap stub; real decls generated once src/ffi.rs lands */\n\
                 #endif /* CEDAR_CABI_H */\n",
            )
            .expect("write stub header");
        }
    }
}
