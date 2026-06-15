import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import type { BlocDto, EpiMatrixDto } from './emplacement-matrix.models';

type BlocMeshState = 'free' | 'occupied' | 'highlight';

interface BlocMeshEntry {
  mesh: THREE.Mesh;
  bloc: BlocDto;
  state: BlocMeshState;
}

@Component({
  selector: 'app-epi-3d-view',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  templateUrl: './epi-3d-view.component.html',
  styleUrl: './epi-3d-view.component.scss',
})
export class Epi3dViewComponent implements AfterViewInit, OnDestroy {
  readonly matrix = input.required<EpiMatrixDto>();
  readonly highlightedBlocIds = input<string[]>([]);

  readonly blocClick = output<BlocDto>();

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('canvasHost');

  private renderer: THREE.WebGLRenderer | null = null;
  private scene: THREE.Scene | null = null;
  private camera: THREE.PerspectiveCamera | null = null;
  private controls: OrbitControls | null = null;
  private animationId: number | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private initialized = false;

  private readonly blocMeshes = new Map<string, BlocMeshEntry>();
  private readonly disposableResources: Array<THREE.Material | THREE.Texture | THREE.BufferGeometry> = [];

  private cardboardMaterial!: THREE.MeshStandardMaterial;
  private freeSlotMaterial!: THREE.MeshStandardMaterial;
  private highlightMaterial!: THREE.MeshStandardMaterial;
  private metalMaterial!: THREE.MeshStandardMaterial;
  private floorMaterial!: THREE.MeshStandardMaterial;

  private readonly highlightEffect = effect(() => {
    const ids = new Set(this.highlightedBlocIds());
    for (const [id, entry] of this.blocMeshes) {
      const next: BlocMeshState = ids.has(id) ? 'highlight' : entry.bloc.boiteId != null ? 'occupied' : 'free';
      if (next !== entry.state) {
        entry.mesh.material = this.materialForState(next);
        entry.state = next;
      }
    }
  });

  private readonly matrixEffect = effect(() => {
    const data = this.matrix();
    if (this.initialized && data) {
      this.rebuildScene(data);
    }
  });

  ngAfterViewInit(): void {
    this.initMaterials();
    this.initRenderer();
    this.initialized = true;
    this.rebuildScene(this.matrix());
  }

  ngOnDestroy(): void {
    if (this.animationId != null) {
      cancelAnimationFrame(this.animationId);
    }
    this.resizeObserver?.disconnect();
    this.controls?.dispose();
    this.renderer?.dispose();
    for (const resource of this.disposableResources) {
      resource.dispose();
    }
    this.blocMeshes.clear();
  }

  private initMaterials(): void {
    const cardboardTex = this.createCardboardTexture();
    const metalTex = this.createMetalTexture();
    const floorTex = this.createConcreteTexture();

    this.cardboardMaterial = new THREE.MeshStandardMaterial({
      map: cardboardTex,
      color: 0xc9a66b,
      roughness: 0.88,
      metalness: 0.04,
    });
    this.freeSlotMaterial = new THREE.MeshStandardMaterial({
      color: 0x334155,
      roughness: 0.95,
      metalness: 0.08,
      transparent: true,
      opacity: 0.55,
    });
    this.highlightMaterial = new THREE.MeshStandardMaterial({
      map: cardboardTex,
      color: 0x60a5fa,
      emissive: 0x1d4ed8,
      emissiveIntensity: 0.45,
      roughness: 0.65,
      metalness: 0.12,
    });
    this.metalMaterial = new THREE.MeshStandardMaterial({
      map: metalTex,
      color: 0x9ca3af,
      roughness: 0.42,
      metalness: 0.78,
    });
    this.floorMaterial = new THREE.MeshStandardMaterial({
      map: floorTex,
      color: 0x94a3b8,
      roughness: 0.92,
      metalness: 0.05,
    });

    this.track(this.cardboardMaterial);
    this.track(this.freeSlotMaterial);
    this.track(this.highlightMaterial);
    this.track(this.metalMaterial);
    this.track(this.floorMaterial);
    this.track(cardboardTex);
    this.track(metalTex);
    this.track(floorTex);
  }

  private initRenderer(): void {
    const el = this.host().nativeElement;
    const width = Math.max(el.clientWidth, 320);
    const height = Math.max(el.clientHeight, 420);

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xe8edf4);
    this.scene.fog = new THREE.Fog(0xe8edf4, 28, 72);

    this.camera = new THREE.PerspectiveCamera(48, width / height, 0.1, 200);
    this.camera.position.set(10, 9, 14);

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.setSize(width, height);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    el.appendChild(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.06;
    this.controls.maxPolarAngle = Math.PI * 0.49;
    this.controls.minDistance = 4;
    this.controls.maxDistance = 48;

    this.renderer.domElement.addEventListener('pointerdown', this.onPointerDown);
    this.resizeObserver = new ResizeObserver(() => this.onResize());
    this.resizeObserver.observe(el);
    this.animate();
  }

  private rebuildScene(data: EpiMatrixDto): void {
    if (!this.scene || !this.camera || !this.controls) {
      return;
    }

    const toRemove = [...this.scene.children];
    for (const child of toRemove) {
      this.scene.remove(child);
    }
    this.blocMeshes.clear();

    const ambient = new THREE.AmbientLight(0xf1f5f9, 0.55);
    const key = new THREE.DirectionalLight(0xffffff, 1.15);
    key.position.set(12, 18, 10);
    key.castShadow = true;
    key.shadow.mapSize.set(2048, 2048);
    const fill = new THREE.DirectionalLight(0xbfdbfe, 0.35);
    fill.position.set(-8, 6, -6);
    this.scene.add(ambient, key, fill);

    const cols = data.traversHeaders.length;
    const rows = data.rows.length;
    const blocsPer = data.epi.blocsPerTablette || 1;

    const BLOC_W = 0.44;
    const BLOC_H = 0.52;
    const BLOC_D = 0.36;
    const SLOT_GAP = 0.06;
    const BAY_W = blocsPer * (BLOC_W + SLOT_GAP) + 0.35;
    const BAY_H = 1.45;
    const BAY_D = 1.05;
    const COL_STEP = BAY_W + 0.75;
    const ROW_STEP = BAY_H + 0.35;

    const totalW = cols * COL_STEP;
    const totalH = rows * ROW_STEP;
    const originX = -totalW / 2 + COL_STEP / 2;
    const originY = 0.12;

    const floor = new THREE.Mesh(new THREE.PlaneGeometry(Math.max(totalW + 6, 12), Math.max(rows * 2.2, 8)), this.floorMaterial);
    floor.rotation.x = -Math.PI / 2;
    floor.position.y = 0;
    floor.receiveShadow = true;
    this.scene.add(floor);

    const highlightIds = new Set(this.highlightedBlocIds());

    for (const row of data.rows) {
      for (const cell of row) {
        const x = originX + cell.colIndex * COL_STEP;
        const y = originY + cell.rowIndex * ROW_STEP;
        this.addRackUnit(x, y, 0, BAY_W, BAY_H, BAY_D);

        const sorted = [...cell.blocs].sort((a, b) => a.positionIndex - b.positionIndex);
        let i = 0;
        while (i < sorted.length) {
          const bloc = sorted[i];
          if (bloc.boiteId == null) {
            const mesh = this.createBlocMesh(BLOC_W, BLOC_H, BLOC_D, 'free');
            mesh.position.set(
              x - BAY_W / 2 + 0.2 + bloc.positionIndex * (BLOC_W + SLOT_GAP) + BLOC_W / 2,
              y + BAY_H * 0.42,
              0.12,
            );
            mesh.userData = { bloc };
            this.scene.add(mesh);
            this.blocMeshes.set(bloc.id, { mesh, bloc, state: 'free' });
            i++;
            continue;
          }

          const group: BlocDto[] = [bloc];
          while (
            i + group.length < sorted.length &&
            sorted[i + group.length].boiteId === bloc.boiteId
          ) {
            group.push(sorted[i + group.length]);
          }
          const span = group.length;
          const segW = span * BLOC_W + Math.max(0, span - 1) * SLOT_GAP;
          const startSlot = group[0].positionIndex;
          const centerX = x - BAY_W / 2 + 0.2 + startSlot * (BLOC_W + SLOT_GAP) + segW / 2;
          const highlighted = group.some((b) => highlightIds.has(b.id));
          const state: BlocMeshState = highlighted ? 'highlight' : 'occupied';
          const mesh = this.createBlocMesh(segW, BLOC_H, BLOC_D, state);
          mesh.material = this.materialForState(state);
          mesh.position.set(centerX, y + BAY_H * 0.42, 0.12);
          mesh.castShadow = true;
          mesh.userData = { bloc: group[0] };
          this.scene.add(mesh);
          for (const entry of group) {
            this.blocMeshes.set(entry.id, { mesh, bloc: entry, state });
          }
          i += group.length;
        }
      }
    }

    const centerY = originY + ((rows - 1) * ROW_STEP) / 2;
    this.controls.target.set(0, centerY + 0.8, 0);
    this.camera.position.set(totalW * 0.55, centerY + rows * 0.9 + 2, totalW * 0.65 + 6);
    this.controls.update();
  }

  private addRackUnit(x: number, y: number, z: number, width: number, height: number, depth: number): void {
    const postGeo = new THREE.BoxGeometry(0.07, height, 0.07);
    this.track(postGeo);
    const offsets: [number, number][] = [
      [-width / 2, -depth / 2],
      [width / 2, -depth / 2],
      [-width / 2, depth / 2],
      [width / 2, depth / 2],
    ];
    for (const [px, pz] of offsets) {
      const post = new THREE.Mesh(postGeo, this.metalMaterial);
      post.position.set(x + px, y + height / 2, z + pz);
      post.castShadow = true;
      this.scene?.add(post);
    }

    const shelfGeo = new THREE.BoxGeometry(width, 0.05, depth);
    this.track(shelfGeo);
    const shelf = new THREE.Mesh(shelfGeo, this.metalMaterial);
    shelf.position.set(x, y + 0.2, z);
    shelf.castShadow = true;
    shelf.receiveShadow = true;
    this.scene?.add(shelf);

    const backGeo = new THREE.BoxGeometry(width, height, 0.04);
    this.track(backGeo);
    const back = new THREE.Mesh(backGeo, this.metalMaterial);
    back.position.set(x, y + height / 2, z - depth / 2);
    back.castShadow = true;
    this.scene?.add(back);
  }

  private createBlocMesh(w: number, h: number, d: number, state: BlocMeshState): THREE.Mesh {
    const geo = new THREE.BoxGeometry(w, h, d);
    this.track(geo);
    const mesh = new THREE.Mesh(geo, this.materialForState(state));
    mesh.castShadow = state !== 'free';
    mesh.receiveShadow = true;
    return mesh;
  }

  private materialForState(state: BlocMeshState): THREE.MeshStandardMaterial {
    switch (state) {
      case 'highlight':
        return this.highlightMaterial;
      case 'occupied':
        return this.cardboardMaterial;
      default:
        return this.freeSlotMaterial;
    }
  }

  private readonly onPointerDown = (event: PointerEvent): void => {
    if (!this.camera || !this.scene || !this.renderer) {
      return;
    }
    const rect = this.renderer.domElement.getBoundingClientRect();
    this.pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    this.pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    this.raycaster.setFromCamera(this.pointer, this.camera);
    const hits = this.raycaster.intersectObjects(this.scene.children, true);
    for (const hit of hits) {
      const bloc = hit.object.userData?.['bloc'] as BlocDto | undefined;
      if (bloc?.boiteId != null) {
        this.blocClick.emit(bloc);
        break;
      }
    }
  };

  private onResize(): void {
    const el = this.host().nativeElement;
    if (!this.renderer || !this.camera) {
      return;
    }
    const width = Math.max(el.clientWidth, 320);
    const height = Math.max(el.clientHeight, 420);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }

  private animate = (): void => {
    this.animationId = requestAnimationFrame(this.animate);
    this.controls?.update();
    if (this.renderer && this.scene && this.camera) {
      this.renderer.render(this.scene, this.camera);
    }
  };

  private track(resource: THREE.Material | THREE.Texture | THREE.BufferGeometry): void {
    this.disposableResources.push(resource);
  }

  private createCardboardTexture(): THREE.CanvasTexture {
    return this.createProceduralTexture(256, 256, (ctx, w, h) => {
      ctx.fillStyle = '#c4a574';
      ctx.fillRect(0, 0, w, h);
      for (let i = 0; i < 1400; i++) {
        const shade = 175 + Math.floor(Math.random() * 45);
        ctx.fillStyle = `rgba(${shade}, ${shade - 25}, ${shade - 55}, 0.18)`;
        ctx.fillRect(Math.random() * w, Math.random() * h, 1 + Math.random() * 2, 1);
      }
      ctx.strokeStyle = 'rgba(120, 83, 46, 0.25)';
      for (let y = 0; y < h; y += 18) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
        ctx.stroke();
      }
    });
  }

  private createMetalTexture(): THREE.CanvasTexture {
    return this.createProceduralTexture(256, 256, (ctx, w, h) => {
      const grad = ctx.createLinearGradient(0, 0, w, h);
      grad.addColorStop(0, '#d1d5db');
      grad.addColorStop(0.5, '#9ca3af');
      grad.addColorStop(1, '#6b7280');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);
      ctx.strokeStyle = 'rgba(255,255,255,0.12)';
      for (let y = 0; y < h; y += 4) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
        ctx.stroke();
      }
    });
  }

  private createConcreteTexture(): THREE.CanvasTexture {
    return this.createProceduralTexture(512, 512, (ctx, w, h) => {
      ctx.fillStyle = '#94a3b8';
      ctx.fillRect(0, 0, w, h);
      for (let i = 0; i < 3000; i++) {
        const g = 100 + Math.floor(Math.random() * 80);
        ctx.fillStyle = `rgba(${g}, ${g + 5}, ${g + 12}, 0.2)`;
        ctx.fillRect(Math.random() * w, Math.random() * h, 2, 2);
      }
    });
  }

  private createProceduralTexture(
    width: number,
    height: number,
    draw: (ctx: CanvasRenderingContext2D, w: number, h: number) => void,
  ): THREE.CanvasTexture {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return new THREE.CanvasTexture(canvas);
    }
    draw(ctx, width, height);
    const tex = new THREE.CanvasTexture(canvas);
    tex.wrapS = THREE.RepeatWrapping;
    tex.wrapT = THREE.RepeatWrapping;
    tex.repeat.set(2, 2);
    tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  }
}
